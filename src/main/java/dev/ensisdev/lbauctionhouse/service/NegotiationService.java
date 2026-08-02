package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.core.config.LanguageManager;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.util.ItemNames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pazarlık (teklif) sistemi — alıcı "teklif açık" ilanlara fiyat teklifi gönderir;
 * satıcı tıklanabilir chat butonlarıyla [Kabul][Reddet][Yeni Teklif] yanıtlar.
 * Kabul edilince alıcıya son onay; alıcı onaylayınca o fiyatla satış yapılır.
 *
 * <p>Kontroller: indirim üst limiti (max-discount-percent), red cooldown
 * (reject-limit), bekleyen teklif üst limiti ve süre aşımı (timeout).</p>
 */
public class NegotiationService {

    private final LbAuctionHouse plugin;
    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionData data;
    private final LanguageManager lang;

    private final Map<UUID, Negotiation> offers = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> buyerActive = new ConcurrentHashMap<>();
    private final Map<String, BlockInfo> blocks = new ConcurrentHashMap<>();

    public NegotiationService(LbAuctionHouse plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = plugin.getAuctionConfig();
        this.data = manager.getData();
        this.lang = manager.getApi().getLanguageManager();
    }

    public enum Status { PENDING, ACCEPTED, REJECTED, COUNTER, EXPIRED, CANCELLED }

    public record Negotiation(UUID id, UUID listingId, UUID buyerUuid, String buyerName,
                              UUID sellerUuid, double price, Status status, long createdAt) {
        public boolean isOpen() { return status == Status.PENDING || status == Status.COUNTER; }
        public boolean isExpired(long now, int timeoutSec) {
            return isOpen() && (now - createdAt) > timeoutSec * 1000L;
        }
    }

    private record BlockInfo(int rejections, long blockUntil) {
        BlockInfo bump(int limit, int cooldownSec) {
            int n = rejections + 1;
            if (n >= limit) return new BlockInfo(0, System.currentTimeMillis() + (long) cooldownSec * 1000L);
            return new BlockInfo(n, blockUntil);
        }
        boolean blocked(long now) { return blockUntil > now; }
    }

    public enum SendResult { OK, OFFERS_DISABLED, SOLD, SELF, BLOCKED, PRICE_NOT_ALLOWED, ACTIVE_LIMIT, ALREADY_OPEN }
    public enum ReplyResult { OK, NOT_FOUND, NOT_YOURS, NOT_OPEN, SOLD }

    public SendResult sendOffer(Player buyer, AuctionListing listing, double price) {
        if (listing == null) return SendResult.SOLD;
        if (!config.isNegotiationEnabled() || !listing.offersEnabled()) return SendResult.OFFERS_DISABLED;
        if (listing.sold()) return SendResult.SOLD;
        if (listing.sellerUUID().equals(buyer.getUniqueId())) return SendResult.SELF;
        if (isBlocked(buyer.getUniqueId(), listing.sellerUUID())) return SendResult.BLOCKED;
        if (!offerPriceAllowed(listing, price)) return SendResult.PRICE_NOT_ALLOWED;
        if (activeCount(buyer.getUniqueId()) >= config.getMaxActiveOffersPerPlayer()) return SendResult.ACTIVE_LIMIT;
        if (hasOpenOfferOnListing(listing.id(), buyer.getUniqueId())) return SendResult.ALREADY_OPEN;

        Negotiation offer = new Negotiation(UUID.randomUUID(), listing.id(),
                buyer.getUniqueId(), buyer.getName(), listing.sellerUUID(),
                price, Status.PENDING, System.currentTimeMillis());
        offers.put(offer.id(), offer);
        buyerActive.computeIfAbsent(buyer.getUniqueId(), k -> new ArrayList<>()).add(offer.id());

        notifySellerOffer(offer, listing);
        buyer.sendMessage(l("pazarlik.offer-sent", "price", fmt(price), "listing", itemName(listing)));
        return SendResult.OK;
    }

    public boolean offerPriceAllowed(AuctionListing listing, double price) {
        return price >= config.getMinPrice() && price <= config.getMaxPrice()
                && price >= config.getOfferFloorPrice(listing.price());
    }

    public ReplyResult acceptBySeller(Player seller, UUID offerId) {
        Negotiation base = findForSeller(seller, offerId);
        if (base == null) return ReplyResult.NOT_YOURS;
        if (!base.isOpen()) return ReplyResult.NOT_OPEN;
        if (listingSold(base.listingId())) return ReplyResult.SOLD;
        replacePrice(base, Status.ACCEPTED);
        sendConfirmToBuyer(base);
        return ReplyResult.OK;
    }

    public ReplyResult rejectBySeller(Player seller, UUID offerId) {
        Negotiation o = findForSeller(seller, offerId);
        if (o == null) return ReplyResult.NOT_YOURS;
        if (!o.isOpen()) return ReplyResult.NOT_OPEN;
        replacePrice(o, Status.REJECTED, o.price());
        recordReject(o.buyerUuid(), o.sellerUuid());
        Player b = player(o.buyerUuid());
        if (b != null) {
            b.sendMessage(l("pazarlik.rejected", "price", fmt(o.price())));
            if (isBlocked(o.buyerUuid(), o.sellerUuid())) {
                b.sendMessage(l("pazarlik.cooldown", "seconds", String.valueOf(config.getNegotiationCooldownSeconds())));
            }
        }
        return ReplyResult.OK;
    }

    public ReplyResult counterBySeller(Player seller, UUID offerId, double newPrice) {
        Negotiation base = findForSeller(seller, offerId);
        if (base == null) return ReplyResult.NOT_YOURS;
        if (!base.isOpen()) return ReplyResult.NOT_OPEN;
        if (newPrice < config.getMinPrice() || newPrice > config.getMaxPrice()) return ReplyResult.NOT_OPEN;
        Negotiation updated = replacePrice(base, Status.COUNTER, newPrice);
        Player buyer = player(base.buyerUuid());
        if (buyer != null) sendBuyerCounter(buyer, updated.id(), newPrice);
        return ReplyResult.OK;
    }

    public ReplyResult counterByBuyer(Player buyer, UUID offerId, double newPrice) {
        Negotiation o = findForBuyer(buyer, offerId);
        if (o == null) return ReplyResult.NOT_YOURS;
        if (!o.isOpen()) return ReplyResult.NOT_OPEN;
        if (newPrice < config.getMinPrice() || newPrice > config.getMaxPrice()) return ReplyResult.NOT_OPEN;
        AuctionListing listing = data.getListing(o.listingId());
        if (listingSold(o.listingId())) return ReplyResult.SOLD;
        Negotiation updated = replacePrice(o, Status.PENDING, newPrice);
        if (listing != null) notifySellerOffer(updated, listing);
        return ReplyResult.OK;
    }

    public ReplyResult acceptBuyer(Player buyer, UUID offerId) {
        Negotiation o = findForBuyer(buyer, offerId);
        if (o == null) return ReplyResult.NOT_YOURS;
        if (!o.isOpen()) return ReplyResult.NOT_OPEN;
        if (listingSold(o.listingId())) return ReplyResult.SOLD;
        replacePrice(o, Status.ACCEPTED);
        sendConfirmToBuyer(o);
        return ReplyResult.OK;
    }

    public ReplyResult rejectBuyer(Player buyer, UUID offerId) {
        Negotiation o = findForBuyer(buyer, offerId);
        if (o == null) return ReplyResult.NOT_YOURS;
        if (!o.isOpen()) return ReplyResult.NOT_OPEN;
        replacePrice(o, Status.REJECTED, o.price());
        Player s = player(o.sellerUuid());
        if (s != null) s.sendMessage(l("pazarlik.buyer-declined", "buyer", o.buyerName()));
        return ReplyResult.OK;
    }

    public Negotiation find(UUID offerId) { return offers.get(offerId); }
    public boolean isAccepted(UUID offerId) {
        Negotiation o = offers.get(offerId);
        return o != null && o.status() == Status.ACCEPTED;
    }
    public void complete(UUID offerId) {
        Negotiation o = offers.remove(offerId);
        if (o != null) buyerActive.getOrDefault(o.buyerUuid(), List.of()).remove(offerId);
    }
    public boolean isBlocked(UUID buyer, UUID seller) {
        BlockInfo b = blocks.get(key(buyer, seller));
        return b != null && b.blocked(System.currentTimeMillis());
    }
    public void cleanup() {
        long now = System.currentTimeMillis();
        offers.entrySet().removeIf(e -> e.getValue().isExpired(now, config.getNegotiationTimeoutSeconds()));
        buyerActive.values().forEach(list -> list.removeIf(id -> !offers.containsKey(id)));
    }

    private int activeCount(UUID buyer) { return buyerActive.getOrDefault(buyer, List.of()).size(); }
    private boolean hasOpenOfferOnListing(UUID listingId, UUID buyer) {
        for (UUID id : buyerActive.getOrDefault(buyer, List.of())) {
            Negotiation o = offers.get(id);
            if (o != null && o.listingId().equals(listingId) && o.isOpen()) return true;
        }
        return false;
    }
    private Negotiation findForSeller(Player seller, UUID offerId) {
        Negotiation o = offers.get(offerId);
        return (o != null && o.sellerUuid().equals(seller.getUniqueId())) ? o : null;
    }
    private Negotiation findForBuyer(Player buyer, UUID offerId) {
        Negotiation o = offers.get(offerId);
        return (o != null && o.buyerUuid().equals(buyer.getUniqueId())) ? o : null;
    }
    private Negotiation replacePrice(Negotiation base, Status status) { return replacePrice(base, status, base.price()); }
    private Negotiation replacePrice(Negotiation base, Status status, double price) {
        Negotiation next = new Negotiation(base.id(), base.listingId(), base.buyerUuid(), base.buyerName(),
                base.sellerUuid(), price, status, base.createdAt());
        offers.put(base.id(), next);
        return next;
    }
    private boolean listingSold(UUID listingId) {
        AuctionListing l = data.getListing(listingId);
        return l == null || l.sold();
    }
    private void recordReject(UUID buyer, UUID seller) {
        String k = key(buyer, seller);
        BlockInfo prev = blocks.get(k);
        BlockInfo nb = (prev == null ? new BlockInfo(0, 0) : prev).bump(config.getRejectLimit(), config.getNegotiationCooldownSeconds());
        blocks.put(k, nb);
    }
    private Player player(UUID uuid) { return Bukkit.getPlayer(uuid); }
    private String fmt(double d) { return manager.getApi().getEconomyManager().format(d); }
    private String key(UUID a, UUID b) { return a.toString() + ":" + b.toString(); }
    private String itemName(AuctionListing listing) { return ItemNames.displayName(listing.item()); }

    private Component l(String key, String... repl) { return lang.getPrefixed(key, repl); }
    private Component btn(String label, String command) {
        return Component.text(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("§7Tıkla")));
    }

    private void notifySellerOffer(Negotiation offer, AuctionListing listing) {
        Player seller = player(offer.sellerUuid());
        if (seller == null) return;
        String cmd = "/" + config.getLangMainCommand() + " teklif";
        seller.sendMessage(l("pazarlik.offer-received",
                        "buyer", offer.buyerName(), "price", fmt(offer.price()), "listing", itemName(listing))
                .append(Component.text(" ")).append(btn("§a[Kabul]", cmd + " kabul " + offer.id()))
                .append(Component.text(" ")).append(btn("§c[Reddet]", cmd + " red " + offer.id()))
                .append(Component.text(" ")).append(btn("§6[Yeni Teklif]", cmd + " yeni " + offer.id())));
    }

    private void sendBuyerCounter(Player buyer, UUID offerId, double price) {
        String cmd = "/" + config.getLangMainCommand() + " teklif";
        buyer.sendMessage(l("pazarlik.seller-counter", "price", fmt(price))
                .append(Component.text(" ")).append(btn("§a[Kabul]", cmd + " kabul " + offerId))
                .append(Component.text(" ")).append(btn("§c[Reddet]", cmd + " red " + offerId))
                .append(Component.text(" ")).append(btn("§6[Yeni Teklif]", cmd + " yeni " + offerId)));
    }

    private void sendConfirmToBuyer(Negotiation o) {
        Player buyer = player(o.buyerUuid());
        if (buyer == null) return;
        buyer.sendMessage(l("pazarlik.confirm-invite", "price", fmt(o.price()))
                .append(Component.text(" "))
                .append(btn("§a[Satın Al ✓]", "/" + config.getLangMainCommand() + " teklif satin " + o.id())));
    }
}
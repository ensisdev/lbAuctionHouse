package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.service.NegotiationService;

import java.util.UUID;

/**
 * /ihale teklif (pazarlık)
 * <ul>
 *   <li>/ihale teklif &lt;ilan-uuid&gt; &lt;fiyat&gt;  → alıcı teklif gönderir</li>
 *   <li>/ihale teklif kabul &lt;offerId&gt;            → satıcı/alıcı kabul eder</li>
 *   <li>/ihale teklif red &lt;offerId&gt;              → reddeder</li>
 *   <li>/ihale teklif yeni &lt;offerId&gt; &lt;fiyat&gt; → karşı teklif</li>
 *   <li>/ihale teklif satin &lt;offerId&gt;            → kabul edilen teklifi satın alır</li>
 * </ul>
 */
public class CmdNegotiate extends AuctionCmd {

    public CmdNegotiate() {
        super("negotiate", "", true);
        setAliases("teklif", "pazarlik", "teklifyap");
        setDescription("İlanlara fiyat teklifi (pazarlık) gönder");
    }

    @Override
    protected void execute() {
        if (player == null) { msg("§cBu komut sadece oyuncular içindir."); return; }
        if (!config.isNegotiationEnabled()) { msg("§cPazarlık sistemi kapalı."); return; }

        if (!hasArg(0)) {
            msg("§cKullanım:");
            msg("§7/" + label + " teklif <ilan-uuid> <fiyat>");
            msg("§7/" + label + " teklif kabul|red|yeni|satin <offerId> [fiyat]");
            return;
        }
        String a = arg(0).toLowerCase();
        switch (a) {
            case "kabul", "accept" -> doReply(true);
            case "red", "reject" -> doReply(false);
            case "yeni", "counter" -> doCounter();
            case "satin", "buy" -> doBuy();
            default -> sendNewOffer();
        }
    }

    private void sendNewOffer() {
        if (args.length < 2) { msg("§cKullanım: /" + label + " teklif <ilan-uuid> <fiyat>"); return; }
        UUID listingId = parseOffer(arg(0));
        if (listingId == null) { msg("pazarlik.err.number"); return; }
        double price;
        try { price = Double.parseDouble(arg(1)); }
        catch (NumberFormatException e) { msg("pazarlik.err.number"); return; }
        AuctionListing listing = manager.getData().getListing(listingId);
        if (listing == null) { msg("§cİlan bulunamadı."); return; }
        var r = manager.getNegotiation().sendOffer(player, listing, price);
        if (r != NegotiationService.SendResult.OK) {
            msg("pazarlik.err." + errKey(r));
        }
    }

    /** kabul/red — satıcı veya alıcı rolüne göre servis çözer. */
    private void doReply(boolean accept) {
        UUID offerId = parseOffer(arg(1));
        if (offerId == null) { msg("§cGeçersiz offerId."); return; }
        var svc = manager.getNegotiation();
        var offer = svc.find(offerId);
        if (offer == null) { msg("§7Teklif bulunamadı / süresi dolmuş."); return; }
        boolean sellerRole = offer.sellerUuid().equals(player.getUniqueId());
        NegotiationService.ReplyResult r = accept
                ? (sellerRole ? svc.acceptBySeller(player, offerId) : svc.acceptBuyer(player, offerId))
                : (sellerRole ? svc.rejectBySeller(player, offerId) : svc.rejectBuyer(player, offerId));
        if (r != NegotiationService.ReplyResult.OK) {
            msg("§cİşlem başarısız: teklif sana ait değil veya açık değil.");
        }
    }

    private void doCounter() {
        if (args.length < 3) { msg("§cKullanım: /" + label + " teklif yeni <offerId> <fiyat>"); return; }
        UUID offerId = parseOffer(arg(1));
        if (offerId == null) { msg("§cGeçersiz offerId."); return; }
        double price;
        try { price = Double.parseDouble(arg(2)); }
        catch (NumberFormatException e) { msg("pazarlik.err.number"); return; }
        var svc = manager.getNegotiation();
        var offer = svc.find(offerId);
        if (offer == null) { msg("§7Teklif bulunamadı."); return; }
        boolean seller = offer.sellerUuid().equals(player.getUniqueId());
        var r = seller ? svc.counterBySeller(player, offerId, price) : svc.counterByBuyer(player, offerId, price);
        if (r != NegotiationService.ReplyResult.OK) msg("§cKarşı teklif gönderilemedi.");
    }

    /** Kabul edilen teklifle satın al (yalnızca alıcı). */
    private void doBuy() {
        UUID offerId = parseOffer(arg(1));
        if (offerId == null) { msg("§cGeçersiz offerId."); return; }
        var svc = manager.getNegotiation();
        var offer = svc.find(offerId);
        if (offer == null || !offer.buyerUuid().equals(player.getUniqueId()) || !svc.isAccepted(offerId)) {
            msg("§7Onaylanmış bir teklifin yok / bu sana ait değil.");
            return;
        }
        AuctionListing listing = manager.getData().getListing(offer.listingId());
        if (listing == null) { msg("§cİlan bulunamadı."); return; }
        var result = manager.buyItem(player, listing, offer.price());
        if (result == AuctionManager.PurchaseResult.SUCCESS) {
            svc.complete(offerId);
            msg("§aSatın alma tamamlandı!");
        } else {
            msg("§cSatın alma tamamlanamadı.");
        }
    }

    private String errKey(NegotiationService.SendResult r) {
        return switch (r) {
            case OFFERS_DISABLED -> "disabled";
            case SOLD -> "sold";
            case SELF -> "self";
            case BLOCKED -> "blocked";
            case PRICE_NOT_ALLOWED -> "price";
            case ACTIVE_LIMIT -> "limit";
            case ALREADY_OPEN -> "open";
            default -> "disabled";
        };
    }

    private UUID parseOffer(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
# CLAUDE.md — lbAuctionHouse

**lbAuctionHouse**, lbSmpCore'dan **bağımsız**, kendi başına çalışan bir ihale (Auction House) pluginidir.
Donut SMP tarzı, tamamen konfigüre edilebilir.

> Bu plugin artık **addon DEĞİLDİR**. lbSmpCore'a `depend` yoktur; tüm altyapı
> (config, dil, veri, ekonomi, GUI) plugin'in kendi `core` paketinde barındırılır.

## Bağımlılıklar

- **Paper/Bukkit API** (1.20+)
- **Vault** (ekonomi) — soft
- **PlaceholderAPI** — soft
- Kendi içinde: SQLite/HikariCP (veri), MySQL desteği, Jedis (Redis cluster)

## Paket Yapısı

```
dev.ensisdev.lbauctionhouse/
├── LbAuctionHouse.java      # Ana plugin sınıfı (JavaPlugin)
├── AuctionManager.java      # Merkezi iş mantığı
├── core/                    # Vendored altyapı (lbSmpCore'dan bağımsız kopya)
│   ├── config/              # ConfigManager, LanguageManager
│   ├── data/                # DataManager, StorageAdapter, SQLite/MySQL, Async
│   ├── economy/             # EconomyManager (Vault)
│   ├── gui/                 # BaseMenu, MenuItem, MenuManager, SignInputGUI, ChatInput
│   ├── event/               # DataLoadEvent, EconomyBalanceUpdateEvent, ConfigReloadEvent
│   └── addon/               # AuctionAPI (facade), AddonLogger
├── config/                  # AuctionConfig, AuctionMessages
├── data/                    # AuctionListing, AuctionData (CRUD)
├── gui/                     # MainMenuGUI, SellGUI, ItemInfoGUI, BundleEditGUI, ...
├── command/                 # AuctionCmdManager, AdminCommandExecutor, Cmd*
├── economy/                 # AuctionEconomy (Vault wrapper + Exp/PlayerPoints)
├── cluster/                 # Local/Redis cluster bridge
├── listener/                # PlayerListener
├── placeholder/             # PlaceholderAPI expansion
└── service/                 # AntiDupeService, ListingCacheService, TradeService, ...
```

## Config Dosyaları (Tümü Özelleştirilebilir)

- `config.yml` — vergi, süre, limit, blacklist, **varsayılan mesaj dili (`lang:`)** — yalnızca messages.yml'de `message-lang` boşsa kullanılır
- `messages.yml` — varsayılan (Türkçe tabanlı) mesajlar + **`message-lang`** seçici (none | tr | en | de | fr | ar); dil dosyasında olmayan anahtarlar buradan düşer
- `commands.yml` — **komut dili (`command-lang:`)** + `main-command`/`aliases` + **`admin-command`**/`admin-aliases` + tüm sub-command/admine override alanları
- `sounds.yml` — GUI ses efektleri
- `gui/main-menu.yml` — ana sayfa layout
- `gui/my-listings.yml` — ilanlarım layout
- `gui/confirm.yml` — satın alma onay layout
- `gui/collection-box.yml` — kutu layout
- `gui/categories.yml` — kategori + materyal listeleri
- `gui/info.yml` — ilan bilgisi GUI'si (item-slot, seller-slot, buy-slot)
- `lang/*.yml` — dil dosyaları (tr, en, de, fr, ar): hem **komut isimleri** hem **o dilin mesajları**

## Dil Sistemi — İki Bağımsız Seçim

- **Mesaj dili** → `messages.yml` içindeki `message-lang:` (none | tr | en | de | fr | ar).
  `none` → messages.yml olduğu gibi kullanılır; bir dil kodu → `lang/<dil>.yml` içindeki `auction.*`
  anahtarları messages.yml üzerine **birleştirilir** (eksik anahtarlar messages.yml'e düşer).
  `message-lang` boşsa `config.yml` içindeki `lang:` kullanılır.
- **Komut dili** → `commands.yml` içindeki `command-lang:`:
  - **`none` (varsayılan):** dil dosyası kullanılmaz — TÜM komutlar doğrudan `commands.yml`'den çekilir
    (hazır Türkçe isimler: `ihale`, `sat`, `aç`, `ilanlarım`, `kutu`, `sil`, `gör` ...).
  - **`tr | en | de | fr | ar`:** komut isimleri `lang/<dil>.yml` içindeki `main-command` / `aliases` /
    `subcommands` / `admin` bölümlerinden gelir.
- İkisi bağımsızdır: Türkçe mesaj + İngilizce komut gibi kombinasyonlar mümkün.
- `commands.yml` içindeki herhangi bir alan DOLDURULURSA seçili dil dosyasındaki değeri EZER;
  boş bırakılırsa dil dosyasındaki değer kullanılır.
- Kod seviyesi varsayılanlar da TÜRKÇE'dir (`SUBCOMMAND_DEFAULTS`): commands.yml ve dil dosyası boş olsa
  bile sub-commandlar Türkçe çalışır (view → `gör`, sell → `sat` vb.).
- Sub-command formatı `[birincil isim, alias1, alias2, ...]` — ilk öğe ASIL ad (tab tamamlama/yardımda
  görünür), sonrakiler kısayol. Tümü yazılarak çalışır.

## Yönetim Komutu (Ayrı)

- Yönetim, ana komutun alt komutu DEĞİLDİR. `/ihale yönet` çalışmaz.
- Yönetim ayrı bir komuttur: `/ihaleadmin` (commands.yml → `admin-command`, varsayılan Türkçe).
  Aliaslar: `admin-aliases` (varsayılan `ahadmin`, `yonetim`).
- Admin alt komutları doğrudan yönetim komutu üzerinden çalışır: `/ihaleadmin stats | logs | clear | remove <uuid> | ban | unban | banlist`.
- Yönetim komutunda tab tamamlama çalışır (alt komutlar, uuid, oyuncu adları).
- `AdminCommandExecutor` (command/framework) `CmdAdmin`'i sarar; `AuctionCmd.runCommand()` public köprüsünü kullanır.

## Sell GUI Davranışı

- Eşya, envanterden **tıklayarak** satış slotuna konur (sürüklemeye gerek yok). Varsayılan miktar = tıklanan destenin adedi.
- **Fiyat TEK'tir (adet başı değil)** — fiyat, ilanın tamamı içindir. `totalPrice = fiyat` (miktarla çarpılmaz).
- **Miktar** +/- butonlarıyla ayarlanır; **+ butonu envanterdeki toplam adedi geçemez** (`countInInventory`).
- **Toplu Paket (Fıçı):** satış GUI'sindeki BARREL (9. slot) → `BundleEditGUI` açar; envanterden tıklayarak birden çok eşya pakete eklenir, "Tamam" ile tek BARREL item'ına paketlenir (`BundleItems`, PDC). Fıçı ilanı ihaleye tek eşya gibi girer; alıcı satın alınca eşyalar paketten AÇILIR ve teker teker verilir.
- Fiyat tabelası kapatıldığında GUI **state korunarak** yeniden açılır (eşya/miktar/fiyat kaybolmaz).
- `BaseMenu.refresh(Player)` açık envanterdeki item'ları günceller; miktar/fiyat değişiklikleri anında görünür.

## İhale GUI Etkileşimleri (Ana Menü)

- **Sol tık** → satın alma onay GUI'si (veya confirm-on-buy false ise direkt satın al).
- **Sağ tık:**
  - Fıçı paketi (BARREL) → `BundleViewGUI` içerik görüntüleyici.
  - **Shulker kutusu** → ayrı `ShulkerViewGUI` önizleme (fıçıdan ayrı).
  - Normal eşya → `ItemInfoGUI` (27 slot: 14 → eşya, 11 → satıcı kafası + istatistik, 17 → satın al). Layout `gui/info.yml`'den özelleştirilebilir.
  - BID ilanı → teklif geçmişi (`BidHistoryGUI`).
- İlan lore'unda tıklama yönergeleri gösterilir: `&8[🛒] &eSol Tıkla &7| Satın Al` ve `&8[👁] &eSağ Tıkla &7| Önizle` (main-menu.yml `lore-format`'ten).

## GUI Başlıkları

- Tüm GUI'lerin başlığı vardır ve **`&` renk kodu** destekler (BaseMenu `setDynamicTitle`).
- Arama sırasında ana menü başlığı: `&8"«sorgu»" &7İçin &e«sonuç» &7Sonuç Bulundu`.
- Layout dosyalarındaki `title:` alanı kullanılır (main-menu.yml, confirm.yml, info.yml).

## Arama Komutu

- `/ihale ara <eşya adı>` → ana menüdeki **arama GUI'sini** aynı sorguyla ve dinamik başlıkla açar (`MainMenuGUI.openWithSearch`).
- Gelişmiş filtre sözdizimi (`min:`, `max:`, `type:`, `seller:`, `mat:`, `ads`) kullanılırsa sohbet araması yapılır.

## Bağımsız Mimari (lbSmpCore'dan Ayrıştırma)

- `core/` paketi, lbSmpCore'un ilgili altyapı sınıflarının **kopyasıdır** (çalışma zamanı bağımlılığı yok).
- `LbAuctionHouse` (JavaPlugin) tüm servisleri kendi kurar: ConfigManager → LanguageManager → DataManager → EconomyManager → MenuManager → AuctionAPI.
- `AuctionAPI` (eski LbSmpCoreAPI), iç servislere erişim sağlayan facade'dır.
- Addon yaşam döngüsü (LbSmpAddon/AddonManager) **kaldırılmıştır**; `onEnable()` her şeyi başlatır.
- `getFile()` protected olduğundan `getPluginJarFile()` public köprüsü GUILayoutLoader için korunur.

## Önemli Kurallar

- HİÇBİR ŞEY hardcode edilmez. Tüm metinler, item'lar, slotlar, sesler config'den okunur.
- Tüm GUI'ler `core/gui/BaseMenu`/`MenuManager` altyapısını kullanır.
- Veri kalıcılığı `core/data/DataManager` üzerinden SQLite/MySQL.
- Ekonomi işlemleri `core/economy/EconomyManager` üzerinden Vault.
- Her config reload'u tüm dosyaları yeniden yükler (sunucu restart gerekmez).
- HİÇBİR exception sessizce yutulmaz — onEnable ve tüm try/catch bloklarında TAM stack trace basılır.

## Yeni Özellikler (Son Güncelleme)

- **Favoriler:** listing-bazlı favori sistemi (`auction_favorites` tablosu).
  Ana menüde slot 47 (`main-menu.yml` → `favorites`) → `FavoritesGUI`; ilana **Shift+Sağ Tık** ile ekle/çıkar;
  `ItemInfoGUI` (16. slot) üzerinden de yönetilebilir. Komut: `/ihale favori`.
- **Kişisel İşlem Geçmişi:** `HistoryGUI` (`auction_logs` tabanlı) — HEPSİ/SATIŞLARIM/ALIMLARIM filtresi.
  Komut: `/ihale geçmiş`.
- **Admin GUI:** `/ihaleadmin` (argümansız) veya `/ihaleadmin panel` → `AdminGUI`.
  İstatistikler, KDV/Vergi Raporu (son 14 günlük döküm), İlan Yönetimi (onaylı silme), Yasak Yönetimi.
- **Listing Fee:** `auction.listing-fee` (config.yml) — her listelemede kesilen sabit ön ücret.
- **Pazarlık Rozeti:** teklif açık ilanlarda isimde 💬 rozet + lore ipucu.
- **Çevrimdışı Satıcı Uyarısı:** join'de cevaplanmamış pazarlık teklifi ve yenilenebilir (süresi dolan) ilan hatırlatması.
- **Onaylı Yenileme:** `/ihale ilanlarım` → slot 47 "Süresi Dolanlar" → sol tık → onay ile yeniden listeleme.
- **Discord Webhook:** yeni ilan, admin eylemi, yasaklama, lootbox ve pazarlık kabulü bildirimleri eklendi.
- **MySQL Uyumluluğu:** `AUTO_INCREMENT`/`VARCHAR` şema düzeltmeleri, `_migrations` MySQL override,
  `INSERT OR IGNORE` → MySQL `INSERT IGNORE` ayrımı (wishlist/favorites).

## Kaldırılan Özellikler

- **Daily Rewards** (günlük ödüller) — config + kod tamamen kaldırıldı.
- **Item Generator** (itemgen şablonları) — config + kod tamamen kaldırıldı.
- **Sealed Bid** (gizli teklif) — DB kolonu ve model alanı kaldırıldı.
- **bStats** — `settings.bstats-enabled` bloğu kaldırıldı (config-version: 5).

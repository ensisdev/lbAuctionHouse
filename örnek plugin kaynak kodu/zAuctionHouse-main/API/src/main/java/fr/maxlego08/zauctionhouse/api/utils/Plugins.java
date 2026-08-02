package fr.maxlego08.zauctionhouse.api.utils;

public enum Plugins {
	
	VAULT("Vault"),
	ESSENTIALS("Essentials"),
	EXECUTABLE_ITEMS("ExecutableItems"),
	EXECUTABLE_BLOCKS("ExecutableBlocks"),
	HEADDATABASE("HeadDatabase"), 
	ZHEAD("zHead"),
	PLACEHOLDER("PlaceholderAPI"),
	CITIZENS("Citizens"),
	TRANSLATIONAPI("TranslationAPI"),
	ZTRANSLATOR("zTranslator"),
	ORAXEN("Oraxen"),
	ITEMSADDER("ItemsAdder"),
	SLIMEFUN("Slimefun"),
	NOVA("Nova"),
	ECO("eco"),
	ZITEMS("zItems"),
	HMCCOSMETICS("HMCCosmetics"),
	JOBS("Jobs"),
	LUCKPERMS("LuckPerms"),
	CRAFTENGINE("CraftEngine"),
	NEXO("Nexo"),
	MAGICCOSMETICS("MagicCosmetics"),
    NEXTGENS("NextGens"),
    MYTHICMOBS("MythicMobs"),
	ZMENUPLUS("zMenuPlus"),
    BREWERYX("BreweryX"),
    MMOITEMS("MMOItems"),
    CRUCIBLE("MythicCrucible"),
    DENIZEN("Denizen"),
    ADVANCEDITEMS("AdvancedItems"),
    CUSTOMCRAFTING("CustomCrafting"),
    ECOITEMS("EcoItems"),
    ZELAUCTION("ZelAuction"),
    AXSHULKERS("AxShulkers")
    ;

	private final String name;

	Plugins(String name) {
		this.name = name;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

}

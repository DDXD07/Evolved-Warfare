package com.evolvedwarfare.faction;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

/**
 * Facciones enemigas del mundo. Cada jugador tiene una reputación con cada una (ver {@link Reputation}): de ella
 * depende si te atacan, si asaltan tu pueblo o si puedes pactar con ellas. Entre ellas son todas enemigas.
 */
public enum Faction {
	/** Bandidos: forajidos de llanuras y bosques. Se les puede sobornar, pero nunca son aliados. */
	BANDITS("bandits", new UUID(0x45574241_4E444954L, 0x5300000000000001L), -50, Reputation.ALLIED_AT - 1, false,
			Formatting.DARK_RED, BossBar.Color.RED, DyeColor.BLACK, DyeColor.RED, 0x5E1B1B),
	/** Vikingos: guerreros del norte, de taigas y llanuras nevadas. */
	VIKINGS("vikings", new UUID(0x45574249_4B494E47L, 0x5300000000000002L), -25, Reputation.MAX, true,
			Formatting.AQUA, BossBar.Color.BLUE, DyeColor.RED, DyeColor.WHITE, 0x6B4A2E),
	/** Piratas: saqueadores de las costas. */
	PIRATES("pirates", new UUID(0x45575049_52415445L, 0x5300000000000003L), -25, Reputation.MAX, true,
			Formatting.DARK_PURPLE, BossBar.Color.PURPLE, DyeColor.BLACK, DyeColor.WHITE, 0x7A1E1E);

	private final String id;
	private final UUID side;
	private final int startReputation;
	private final int maxReputation;
	private final boolean respectsWhiteFlag;
	private final Formatting color;
	private final BossBar.Color barColor;
	private final DyeColor shieldColor;
	private final DyeColor shieldPatternColor;
	private final int clothColor;

	Faction(String id, UUID side, int startReputation, int maxReputation, boolean respectsWhiteFlag, Formatting color,
			BossBar.Color barColor, DyeColor shieldColor, DyeColor shieldPatternColor, int clothColor) {
		this.id = id;
		this.side = side;
		this.startReputation = startReputation;
		this.maxReputation = maxReputation;
		this.respectsWhiteFlag = respectsWhiteFlag;
		this.color = color;
		this.barColor = barColor;
		this.shieldColor = shieldColor;
		this.shieldPatternColor = shieldPatternColor;
		this.clothColor = clothColor;
	}

	public String id() {
		return id;
	}

	/** Bando (para que no se hagan daño entre ellos). */
	public UUID side() {
		return side;
	}

	public int startReputation() {
		return startReputation;
	}

	public int maxReputation() {
		return maxReputation;
	}

	/** Con la bandera blanca en la mano no te atacan (los bandidos no la respetan). */
	public boolean respectsWhiteFlag() {
		return respectsWhiteFlag;
	}

	/** Se puede firmar una alianza (los bandidos, no). */
	public boolean canAlly() {
		return maxReputation >= Reputation.ALLIED_AT;
	}

	public Formatting color() {
		return color;
	}

	public BossBar.Color barColor() {
		return barColor;
	}

	public DyeColor shieldColor() {
		return shieldColor;
	}

	public DyeColor shieldPatternColor() {
		return shieldPatternColor;
	}

	/** Color de la ropa de cuero teñida que llevan. */
	public int clothColor() {
		return clothColor;
	}

	/** Todas las facciones son enemigas entre sí. */
	public boolean enemies(Faction other) {
		return other != this;
	}

	public String translationKey() {
		return "faction.evolvedwarfare." + id;
	}

	public MutableText displayName() {
		return Text.translatable(translationKey()).formatted(color);
	}

	public static Faction byOrdinal(int ordinal) {
		Faction[] values = values();
		return values[Math.max(0, Math.min(ordinal, values.length - 1))];
	}

	public static Faction byId(String id) {
		for (Faction faction : values()) {
			if (faction.id.equals(id.toLowerCase(Locale.ROOT))) {
				return faction;
			}
		}
		return null;
	}
}

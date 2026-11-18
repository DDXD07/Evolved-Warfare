package com.evolvedwarfare.skill;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Un engrama (fase 113, como en ARK): un grupo de recetas que se aprende con puntos de engrama al llegar a su nivel (y
 * con los anteriores aprendidos). Lo que desbloquea sale de data/evolvedwarfare/engrams/engrams.json (lo escribe
 * tools/ew_engrams.py) y de las recetas que haya: ver {@link Engrams}.
 */
public final class Engram {
	public final String id;
	public final String category;
	public final int level;
	public final int cost;
	@Nullable
	public final Identifier icon;
	public final List<String> requires;
	final List<Identifier> items;
	final List<Pattern> patterns;
	final List<Identifier> tags;
	/** La ventaja de antes que da (forja, mando, asedio), o null. */
	@Nullable
	public final Perk perk;
	public final int perkRank;
	/** Para lo que no encaja en ningún otro: armor, tool, food, block o item (o null si no es de esos). */
	@Nullable
	final String fallback;
	@Nullable
	final String namespace;

	Engram(String id, String category, int level, int cost, @Nullable Identifier icon, List<String> requires, List<Identifier> items,
			List<Pattern> patterns, List<Identifier> tags, @Nullable Perk perk, int perkRank, @Nullable String fallback, @Nullable String namespace) {
		this.id = id;
		this.category = category;
		this.level = level;
		this.cost = cost;
		this.icon = icon;
		this.requires = requires;
		this.items = items;
		this.patterns = patterns;
		this.tags = tags;
		this.perk = perk;
		this.perkRank = perkRank;
		this.fallback = fallback;
		this.namespace = namespace;
	}

	public String translationKey() {
		return "engram.evolvedwarfare." + id;
	}

	public static String categoryKey(String category) {
		return "engram_category.evolvedwarfare." + category;
	}
}

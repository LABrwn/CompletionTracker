package de.labrwn.completiontracker.track;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.panda.Panda;

import java.util.List;

/** Reads the visual variant of a mob (sheep colour, cat breed, wolf type ...) as a short id. */
public final class Variants {
	private static final List<DataComponentType<?>> TYPES = List.of(
			DataComponents.SHEEP_COLOR, DataComponents.CAT_VARIANT, DataComponents.WOLF_VARIANT,
			DataComponents.COW_VARIANT, DataComponents.PIG_VARIANT, DataComponents.CHICKEN_VARIANT,
			DataComponents.FROG_VARIANT, DataComponents.HORSE_VARIANT, DataComponents.RABBIT_VARIANT,
			DataComponents.PARROT_VARIANT, DataComponents.LLAMA_VARIANT, DataComponents.AXOLOTL_VARIANT,
			DataComponents.FOX_VARIANT, DataComponents.MOOSHROOM_VARIANT, DataComponents.VILLAGER_VARIANT,
			DataComponents.ZOMBIE_NAUTILUS_VARIANT);

	private Variants() {
	}

	/** The variant id, or null if this mob has no variant we track. */
	public static String of(Entity entity) {
		if (entity instanceof Panda panda) {
			return name(panda.getVariant());
		}
		for (DataComponentType<?> type : TYPES) {
			Object value = entity.get(type);
			if (value != null) {
				String v = name(value);
				// A rabbit named "Toast" is its own variant
				if (v != null && entity.hasCustomName() && "Toast".equals(entity.getCustomName().getString())
						&& type == DataComponents.RABBIT_VARIANT) {
					return "toast";
				}
				return v;
			}
		}
		return null;
	}

	private static String name(Object value) {
		if (value instanceof Holder<?> holder) {
			return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
		}
		if (value instanceof StringRepresentable s) {
			return s.getSerializedName();
		}
		return value == null ? null : value.toString().toLowerCase(java.util.Locale.ROOT);
	}
}

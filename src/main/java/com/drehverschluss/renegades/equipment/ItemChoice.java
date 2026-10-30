package com.drehverschluss.renegades.equipment;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The value of one equipment slot: one random option of the list; a tag option picks a random item of the tag.
 */
public record ItemChoice(List<Option> options) {
    public ItemStack pick(RandomSource random) {
        return this.options.get(random.nextInt(this.options.size())).pick(random);
    }

    public sealed interface Option permits ItemOption, TagOption {
        ItemStack pick(RandomSource random);
    }

    public record ItemOption(Item item) implements Option {
        @Override
        public ItemStack pick(RandomSource random) {
            return new ItemStack(this.item);
        }

        @Override
        public String toString() {
            return BuiltInRegistries.ITEM.getKey(this.item).toString();
        }
    }

    public record TagOption(TagKey<Item> tag) implements Option {
        @Override
        public ItemStack pick(RandomSource random) {
            List<Holder<Item>> items = items();
            return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())));
        }

        public boolean isEmpty() {
            return items().isEmpty();
        }

        private List<Holder<Item>> items() {
            return BuiltInRegistries.ITEM.getTag(this.tag).map(set -> set.stream().toList()).orElse(List.of());
        }

        @Override
        public String toString() {
            return "#" + this.tag.location();
        }
    }

    @Override
    public String toString() {
        return this.options.size() == 1 ? this.options.getFirst().toString() : this.options.toString();
    }
}

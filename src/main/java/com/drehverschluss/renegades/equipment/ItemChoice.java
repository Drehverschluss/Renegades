package com.drehverschluss.renegades.equipment;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The value of one equipment slot: one random option of the list; a tag option picks a random item of the tag. Each
 * option may carry enchantments.
 */
public record ItemChoice(List<Option> options) {
    public ItemStack pick(RandomSource random, RegistryAccess registries) {
        Option option = this.options.get(random.nextInt(this.options.size()));
        return option.enchanting().apply(option.pick(random), random, registries);
    }

    /**
     * Whether the stack is one of the options' items (or in one of their tags); enchantments do not matter.
     */
    public boolean matches(ItemStack stack) {
        for (Option option : this.options) {
            if (option instanceof ItemOption itemOption ? stack.is(itemOption.item()) : stack.is(((TagOption) option).tag())) {
                return true;
            }
        }
        return false;
    }

    public sealed interface Option permits ItemOption, TagOption {
        ItemStack pick(RandomSource random);

        Enchanting enchanting();

        Option withEnchanting(Enchanting enchanting);
    }

    public record ItemOption(Item item, Enchanting enchanting) implements Option {
        @Override
        public ItemStack pick(RandomSource random) {
            return new ItemStack(this.item);
        }

        @Override
        public Option withEnchanting(Enchanting enchanting) {
            return new ItemOption(this.item, enchanting);
        }

        @Override
        public String toString() {
            return BuiltInRegistries.ITEM.getKey(this.item) + (this.enchanting.isNone() ? "" : " " + this.enchanting);
        }
    }

    public record TagOption(TagKey<Item> tag, Enchanting enchanting) implements Option {
        @Override
        public ItemStack pick(RandomSource random) {
            List<Holder<Item>> items = items();
            return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())));
        }

        @Override
        public Option withEnchanting(Enchanting enchanting) {
            return new TagOption(this.tag, enchanting);
        }

        public boolean isEmpty() {
            return items().isEmpty();
        }

        private List<Holder<Item>> items() {
            return BuiltInRegistries.ITEM.getTag(this.tag).map(set -> set.stream().toList()).orElse(List.of());
        }

        @Override
        public String toString() {
            return "#" + this.tag.location() + (this.enchanting.isNone() ? "" : " " + this.enchanting);
        }
    }

    @Override
    public String toString() {
        return this.options.size() == 1 ? this.options.getFirst().toString() : this.options.toString();
    }
}
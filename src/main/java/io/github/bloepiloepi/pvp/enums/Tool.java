package io.github.bloepiloepi.pvp.enums;

import io.github.bloepiloepi.pvp.utils.ModifierUUID;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.attribute.AttributeModifier;
import net.minestom.server.attribute.AttributeOperation;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.attribute.ItemAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum Tool {
	WOODEN_SWORD(ToolMaterial.WOOD, 3, -2.4F),
	STONE_SWORD(ToolMaterial.STONE, 3, -2.4F),
	IRON_SWORD(ToolMaterial.IRON, 3, -2.4F),
	DIAMOND_SWORD(ToolMaterial.DIAMOND, 3, -2.4F),
	GOLDEN_SWORD(ToolMaterial.GOLD, 3, -2.4F),
	NETHERITE_SWORD(ToolMaterial.NETHERITE, 3, -2.4F),
	
	WOODEN_SHOVEL(ToolMaterial.WOOD, 1.5F, -3.0F),
	STONE_SHOVEL(ToolMaterial.STONE, 1.5F, -3.0F),
	IRON_SHOVEL(ToolMaterial.IRON, 1.5F, -3.0F),
	DIAMOND_SHOVEL(ToolMaterial.DIAMOND, 1.5F, -3.0F),
	GOLDEN_SHOVEL(ToolMaterial.GOLD, 1.5F, -3.0F),
	NETHERITE_SHOVEL(ToolMaterial.NETHERITE, 1.5F, -3.0F),
	
	WOODEN_PICKAXE(ToolMaterial.WOOD, 1, -2.8F),
	STONE_PICKAXE(ToolMaterial.STONE, 1, -2.8F),
	IRON_PICKAXE(ToolMaterial.IRON, 1, -2.8F),
	DIAMOND_PICKAXE(ToolMaterial.DIAMOND, 1, -2.8F),
	GOLDEN_PICKAXE(ToolMaterial.GOLD, 1, -2.8F),
	NETHERITE_PICKAXE(ToolMaterial.NETHERITE, 1, -2.8F),
	
	WOODEN_AXE(ToolMaterial.WOOD, 6.0F, -3.2F, true),
	STONE_AXE(ToolMaterial.STONE, 7.0F, -3.2F, true),
	IRON_AXE(ToolMaterial.IRON, 6.0F, -3.1F, true),
	DIAMOND_AXE(ToolMaterial.DIAMOND, 5.0F, -3.0F, true),
	GOLDEN_AXE(ToolMaterial.GOLD, 6.0F, -3.0F, true),
	NETHERITE_AXE(ToolMaterial.NETHERITE, 5.0F, -3.0F, true),
	
	WOODEN_HOE(ToolMaterial.WOOD, 0, -3.0F),
	STONE_HOE(ToolMaterial.STONE, -1, -2.0F),
	IRON_HOE(ToolMaterial.IRON, -2, -1.0F),
	DIAMOND_HOE(ToolMaterial.DIAMOND, -3, 0.0F),
	GOLDEN_HOE(ToolMaterial.GOLD, 0, -3.0F),
	NETHERITE_HOE(ToolMaterial.NETHERITE, -4, 0.0F),
	
	TRIDENT(null, 8.0F, -2.9000000953674316F);
	
	private final Material material;
	private boolean isAxe = false;
	
	private final Map<Attribute, AttributeModifier> attributeModifiers = new HashMap<>();
	
	Tool(@Nullable ToolMaterial toolMaterial, float attackDamage, float attackSpeed) {
		float finalAttackDamage = attackDamage + (toolMaterial == null ? 0 : toolMaterial.getAttackDamage());
		this.material = Material.valueOf(this.name());
		
		this.attributeModifiers.put(Attribute.ATTACK_DAMAGE, new AttributeModifier(ModifierUUID.ATTACK_DAMAGE_MODIFIER_ID, "Tool modifier", finalAttackDamage, AttributeOperation.ADDITION));
		this.attributeModifiers.put(Attribute.ATTACK_SPEED, new AttributeModifier(ModifierUUID.ATTACK_SPEED_MODIFIER_ID, "Tool modifier", attackSpeed, AttributeOperation.ADDITION));
	}
	
	Tool(@Nullable ToolMaterial toolMaterial, float attackDamage, float attackSpeed, boolean isAxe) {
		this(toolMaterial, attackDamage, attackSpeed);
		this.isAxe = isAxe;
	}
	
	public Map<Attribute, AttributeModifier> getAttributes(EquipmentSlot slot, ItemStack item) {
		Map<Attribute, AttributeModifier> modifiers = new HashMap<>();
		for (ItemAttribute itemAttribute : item.getMeta().getAttributes()) {
			if (EquipmentSlot.fromAttributeSlot(itemAttribute.getSlot()) == slot) {
				modifiers.put(itemAttribute.getAttribute(), new AttributeModifier(itemAttribute.getUuid(), itemAttribute.getInternalName(), (float) itemAttribute.getValue(), itemAttribute.getOperation()));
			}
		}
		
		//Weapon attributes (attack damage, etc) do not apply in offhand
		if (slot == EquipmentSlot.MAIN_HAND) {
			modifiers.putAll(this.attributeModifiers);
		}
		
		return modifiers;
	}
	
	public boolean isAxe() {
		return isAxe;
	}
	
	public static Tool fromMaterial(Material material) {
		for (Tool tool : values()) {
			if (tool.material == material) {
				return tool;
			}
		}
		
		return null;
	}
}
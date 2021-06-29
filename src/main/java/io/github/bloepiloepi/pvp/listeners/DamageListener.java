package io.github.bloepiloepi.pvp.listeners;

import io.github.bloepiloepi.pvp.damage.CustomDamageType;
import io.github.bloepiloepi.pvp.damage.CustomEntityDamage;
import io.github.bloepiloepi.pvp.enchantment.EnchantmentUtils;
import io.github.bloepiloepi.pvp.entities.EntityUtils;
import io.github.bloepiloepi.pvp.entities.Tracker;
import io.github.bloepiloepi.pvp.events.DamageBlockEvent;
import io.github.bloepiloepi.pvp.events.FinalDamageEvent;
import io.github.bloepiloepi.pvp.events.TotemUseEvent;
import io.github.bloepiloepi.pvp.utils.DamageUtils;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.*;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.sound.SoundEvent;

public class DamageListener {
	
	public static void register(EventNode<EntityEvent> eventNode) {
		EventNode<EntityEvent> node = EventNode.type("damage-events", EventFilter.ENTITY);
		eventNode.addChild(node);
		
		node.addListener(EntityDamageEvent.class, event -> {
			//TODO player has extra calculations based on difficulty
			
			if (event.isCancelled()) return;
			float amount = event.getDamage();
			
			CustomDamageType type;
			if (event.getDamageType() instanceof CustomDamageType) {
				type = (CustomDamageType) event.getDamageType();
			} else {
				if (event.getDamageType() == DamageType.GRAVITY) {
					type = CustomDamageType.FALL;
				} else if (event.getDamageType() == DamageType.ON_FIRE) {
					type = CustomDamageType.ON_FIRE;
				} else {
					type = CustomDamageType.OUT_OF_WORLD;
				}
			}
			
			LivingEntity entity = event.getEntity();
			if (type.isFire() && EntityUtils.hasEffect(entity, PotionEffect.FIRE_RESISTANCE)) {
				event.setCancelled(true);
				return;
			}
			
			if (type.damagesHelmet() && !entity.getEquipment(EquipmentSlot.HELMET).isAir()) {
				//TODO damage helmet item
				amount *= 0.75F;
			}
			
			Entity attacker = null;
			if (type instanceof CustomEntityDamage) {
				attacker = type.getDirectEntity();
			}
			
			boolean shield = false;
			if (amount > 0.0F && EntityUtils.blockedByShield(entity, type)) {
				DamageBlockEvent damageBlockEvent = new DamageBlockEvent(entity);
				EventDispatcher.call(damageBlockEvent);
				
				//TODO damage shield item
				
				if (!damageBlockEvent.isCancelled()) {
					amount = 0.0F;
					
					if (!type.isProjectile()) {
						if (attacker instanceof LivingEntity) {
							EntityUtils.takeShieldHit(entity, (LivingEntity) attacker, damageBlockEvent.knockbackAttacker());
						}
					}
					
					shield = true;
				}
			}
			
			boolean hurtSoundAndAnimation = true;
			if (Tracker.invulnerableTime.getOrDefault(entity.getUuid(), 0) > 10.0F) {
				float lastDamage = Tracker.lastDamageTaken.get(entity.getUuid());
				
				if (amount <= lastDamage) {
					event.setCancelled(true);
					return;
				}
				
				Tracker.lastDamageTaken.put(entity.getUuid(), amount);
				amount = applyDamage(entity, type, amount - lastDamage);
				hurtSoundAndAnimation = false;
			} else {
				Tracker.lastDamageTaken.put(entity.getUuid(), amount);
				Tracker.invulnerableTime.put(entity.getUuid(), 20);
				amount = applyDamage(entity, type, amount);
			}
			
			FinalDamageEvent finalDamageEvent = new FinalDamageEvent(entity, type, amount);
			EventDispatcher.call(finalDamageEvent);
			
			amount = finalDamageEvent.getDamage();
			
			if (finalDamageEvent.isCancelled() || finalDamageEvent.getDamage() <= 0.0F) {
				event.setCancelled(true);
				return;
			}
			
			if (hurtSoundAndAnimation) {
				if (shield) {
					entity.triggerStatus((byte) 29);
				} else if (type instanceof CustomEntityDamage && ((CustomEntityDamage) type).isThorns()) {
					entity.triggerStatus((byte) 33);
				} else {
					byte status;
					if (type == CustomDamageType.DROWN) {
						//Drown sound and animation
						status = 36;
					} else if (type.isFire()) {
						//Burn sound and animation
						status = 37;
					} else if (type == CustomDamageType.SWEET_BERRY_BUSH) {
						//Sweet berry bush sound and animation
						status = 44;
					} else if (type == CustomDamageType.FREEZE) {
						//Freeze sound and animation
						status = 57;
					} else {
						//Damage sound and animation
						status = 2;
					}
					
					entity.triggerStatus(status);
				}
				
				if (attacker != null && !shield) {
					double h = attacker.getPosition().getX() - entity.getPosition().getX();
					
					double i;
					for(i = attacker.getPosition().getZ() - entity.getPosition().getZ(); h * h + i * i < 1.0E-4D; i = (Math.random() - Math.random()) * 0.01D) {
						h = (Math.random() - Math.random()) * 0.01D;
					}
					
					entity.takeKnockback(0.4F, h, i);
				}
			}
			
			if (shield) {
				event.setCancelled(true);
				return;
			}
			
			SoundEvent sound = null;
			
			float totalHealth = entity.getHealth() + (entity instanceof Player ? ((Player) entity).getAdditionalHearts() : 0);
			if (totalHealth - amount <= 0) {
				boolean totem = totemProtection(entity, type);
				
				if (totem) {
					event.setCancelled(true);
				} else if (hurtSoundAndAnimation) {
					//Death sound
					sound = type.getDeathSound(entity);
				}
			} else if (hurtSoundAndAnimation) {
				//Damage sound
				sound = type.getSound(entity);
			}
			
			//Play sound
			if (sound != null) {
				Sound.Source soundCategory;
				if (entity instanceof Player) {
					soundCategory = Sound.Source.PLAYER;
				} else {
					// TODO: separate living entity categories
					soundCategory = Sound.Source.HOSTILE;
				}
				
				SoundEffectPacket damageSoundPacket =
						SoundEffectPacket.create(soundCategory, sound,
								entity.getPosition(),
								1.0f, 1.0f);
				entity.sendPacketToViewersAndSelf(damageSoundPacket);
			}
			
			event.setDamage(amount);
		});
	}
	
	public static boolean totemProtection(LivingEntity entity, CustomDamageType type) {
		if (type.isOutOfWorld()) return false;
		
		boolean hasTotem = false;
		
		for (Player.Hand hand : Player.Hand.values()) {
			ItemStack stack = entity.getItemInHand(hand);
			if (stack.getMaterial() == Material.TOTEM_OF_UNDYING) {
				TotemUseEvent totemUseEvent = new TotemUseEvent(entity, hand);
				EventDispatcher.call(totemUseEvent);
				
				if (totemUseEvent.isCancelled()) continue;
				
				hasTotem = true;
				entity.setItemInHand(hand, stack.withAmount(stack.getAmount() - 1));
				break;
			}
		}
		
		if (hasTotem) {
			entity.setHealth(1.0F);
			entity.clearEffects();
			entity.addEffect(new Potion(PotionEffect.REGENERATION, (byte) 1, 900));
			entity.addEffect(new Potion(PotionEffect.ABSORPTION, (byte) 1, 100));
			entity.addEffect(new Potion(PotionEffect.FIRE_RESISTANCE, (byte) 0, 800));
			
			//Totem particles
			entity.triggerStatus((byte) 35);
		}
		
		return hasTotem;
	}
	
	public static float applyDamage(LivingEntity entity, CustomDamageType type, float amount) {
		amount = applyArmorToDamage(entity, type, amount);
		amount = applyEnchantmentsToDamage(entity, type, amount);
		
		if (amount != 0.0F && entity instanceof Player) {
			EntityUtils.addExhaustion((Player) entity, type.getExhaustion());
		}
		
		return amount;
	}
	
	public static float applyArmorToDamage(LivingEntity entity, CustomDamageType type, float amount) {
		if (!type.bypassesArmor()) {
			amount = DamageUtils.getDamageLeft(amount, (float) Math.floor(entity.getAttributeValue(Attribute.ARMOR)), entity.getAttributeValue(Attribute.ARMOR_TOUGHNESS));
		}
		
		return amount;
	}
	
	public static float applyEnchantmentsToDamage(LivingEntity entity, CustomDamageType type, float amount) {
		if (type.isUnblockable()) {
			return amount;
		} else {
			int k;
			if (EntityUtils.hasEffect(entity, PotionEffect.DAMAGE_RESISTANCE)) {
				k = (EntityUtils.getEffect(entity, PotionEffect.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
				int j = 25 - k;
				float f = amount * (float) j;
				amount = Math.max(f / 25.0F, 0.0F);
			}
			
			if (amount <= 0.0F) {
				return 0.0F;
			} else {
				k = EnchantmentUtils.getProtectionAmount(EntityUtils.getArmorItems(entity), type);
				if (k > 0) {
					amount = DamageUtils.getInflictedDamage(amount, (float) k);
				}
				
				return amount;
			}
		}
	}
}
package io.github.bloepiloepi.pvp.listeners;

import io.github.bloepiloepi.pvp.damage.CustomDamageType;
import io.github.bloepiloepi.pvp.enchantment.EnchantmentUtils;
import io.github.bloepiloepi.pvp.entities.EntityGroup;
import io.github.bloepiloepi.pvp.entities.EntityUtils;
import io.github.bloepiloepi.pvp.entities.Tracker;
import io.github.bloepiloepi.pvp.utils.SoundManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.entity.*;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.particle.ParticleCreator;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.Position;
import net.minestom.server.utils.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttackManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(AttackManager.class);
	
	public static void register(EventNode<EntityEvent> eventNode) {
		EventNode<EntityEvent> node = EventNode.type("attack-events", EventFilter.ENTITY);
		eventNode.addChild(node);
		
		node.addListener(EntityAttackEvent.class, event -> {
			if (event.getEntity() instanceof Player) {
				onEntityHit((Player) event.getEntity(), event.getTarget());
			}
		});
		
		node.addListener(PlayerHandAnimationEvent.class, event -> resetLastAttackedTicks(event.getPlayer()));
		
		node.addListener(PlayerChangeHeldSlotEvent.class, event -> {
			if (!event.getPlayer().getItemInMainHand().isSimilar(event.getPlayer().getInventory().getItemStack(event.getSlot()))) {
				resetLastAttackedTicks(event.getPlayer());
			}
		});
		
		node.addListener(PlayerUseItemEvent.class, event -> {
			if (Tracker.hasCooldown(event.getPlayer(), event.getItemStack().getMaterial())) {
				event.setCancelled(true);
			}
		});
	}
	
	public static float getAttackCooldownProgressPerTick(Player player) {
		return (float) (1.0D / player.getAttributeValue(Attribute.ATTACK_SPEED) * 20.0D);
	}
	
	public static float getAttackCooldownProgress(Player player, float baseTime) {
		return MathUtils.clamp(((float) Tracker.lastAttackedTicks.get(player.getUuid()) + baseTime) / getAttackCooldownProgressPerTick(player), 0.0F, 1.0F);
	}
	
	public static void resetLastAttackedTicks(Player player) {
		Tracker.lastAttackedTicks.put(player.getUuid(), 0);
	}
	
	public static void onEntityHit(Player player, Entity target) {
		if (target == null) return;
		if (player.isDead()) return;
		if (player.getDistanceSquared(target) >= 36.0D) return;
		
		if (target instanceof ItemEntity || target instanceof ExperienceOrb || target instanceof EntityProjectile || target == player) {
			player.kick(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"));
			LOGGER.error("Player " + player.getUsername() + " tried to attack invalid mob");
			return;
		}
		
		performAttack(player, target);
	}
	
	public static void performAttack(Player player, Entity target) {
		if (player.getGameMode() == GameMode.SPECTATOR) {
			player.spectate(target);
			return;
		}
		
		float damage = player.getAttributeValue(Attribute.ATTACK_DAMAGE);
		float enchantedDamage;
		if (target instanceof LivingEntity) {
			enchantedDamage = EnchantmentUtils.getAttackDamage(player.getItemInMainHand(), EntityGroup.ofEntity((LivingEntity) target));
		} else {
			enchantedDamage = EnchantmentUtils.getAttackDamage(player.getItemInMainHand(), EntityGroup.DEFAULT);
		}
		
		float i = getAttackCooldownProgress(player, 0.5F);
		damage *= 0.2F + i * i * 0.8F;
		enchantedDamage *= i;
		resetLastAttackedTicks(player);
		
		boolean strongAttack = i > 0.9F;
		boolean bl2 = false;
		int knockback = EnchantmentUtils.getKnockback(player);
		if (player.isSprinting() && strongAttack) {
			SoundManager.sendToAround(player, SoundEvent.PLAYER_ATTACK_KNOCKBACK, Sound.Source.PLAYER, 1.0F, 1.0F);
			knockback++;
			bl2 = true;
		}
		
		boolean critical = strongAttack && !EntityUtils.isClimbing(player) && Tracker.falling.get(player.getUuid()) && !player.isOnGround() && !EntityUtils.hasEffect(player, PotionEffect.BLINDNESS) && player.getVehicle() == null && target instanceof LivingEntity && !player.isSprinting();
		if (critical) {
			damage *= 1.5F;
		}
		
		damage += enchantedDamage;
		boolean sweeping = false;
		//TODO formula for sweeping
		
		float targetHealth = 0.0F;
		boolean fireAspectApplied = false;
		int fireAspect = EnchantmentUtils.getFireAspect(player);
		if (target instanceof LivingEntity) {
			targetHealth = ((LivingEntity) target).getHealth();
			if (fireAspect > 0 && !target.isOnFire()) {
				fireAspectApplied = true;
				EntityUtils.setOnFireForSeconds((LivingEntity) target, 1);
			}
		}
		
		Vector vec3d = target.getVelocity();
		boolean damageSucceeded = EntityUtils.damage(target, CustomDamageType.player(player), damage);
		
		if (!damageSucceeded) {
			SoundManager.sendToAround(player, SoundEvent.PLAYER_ATTACK_NODAMAGE, Sound.Source.PLAYER, 1.0F, 1.0F);
			if (fireAspectApplied) {
				target.setOnFire(false);
			}
			return;
		}
		
		if (knockback > 0) {
			if (target instanceof LivingEntity) {
				target.takeKnockback(knockback * 0.5F, Math.sin(player.getPosition().getYaw() * 0.017453292F), -Math.cos(player.getPosition().getYaw() * 0.017453292F));
			} else {
				target.setVelocity(target.getVelocity().add(-Math.sin(player.getPosition().getYaw() * 0.017453292F) * (float) knockback * 0.5F, 0.1D, Math.cos(player.getPosition().getYaw() * 0.017453292F) * (float) knockback * 0.5F));
			}
			
			player.setVelocity(player.getVelocity().multiply(new Vector(0.6D, 1.0D, 0.6D))); //TODO Is this necessary?
			player.setSprinting(false);
		}
		
		if (sweeping) {
			//TODO sweeping
		}
		
		//if (target instanceof Player) {
		//	((Player) target).getPlayerConnection().sendPacket(new EntityVelocityPacket());
		//	target.setVelocity(vec3d);
		//}
		
		if (critical) {
			SoundManager.sendToAround(player, SoundEvent.PLAYER_ATTACK_CRIT, Sound.Source.PLAYER, 1.0F, 1.0F);
			
			EntityAnimationPacket packet = new EntityAnimationPacket();
			packet.entityId = target.getEntityId();
			packet.animation = EntityAnimationPacket.Animation.CRITICAL_EFFECT;
			player.sendPacketToViewersAndSelf(new EntityAnimationPacket());
		}
		
		if (!critical && !sweeping) {
			if (strongAttack) {
				SoundManager.sendToAround(player, SoundEvent.PLAYER_ATTACK_STRONG, Sound.Source.PLAYER, 1.0F, 1.0F);
			} else {
				SoundManager.sendToAround(player, SoundEvent.PLAYER_ATTACK_WEAK, Sound.Source.PLAYER, 1.0F, 1.0F);
			}
		}
		
		if (enchantedDamage > 0.0F) {
			EntityAnimationPacket packet = new EntityAnimationPacket();
			packet.entityId = target.getEntityId();
			packet.animation = EntityAnimationPacket.Animation.MAGICAL_CRITICAL_EFFECT;
			player.sendPacketToViewersAndSelf(new EntityAnimationPacket());
		}
		
		if (target instanceof LivingEntity) {
			EnchantmentUtils.onUserDamaged((LivingEntity) target, player);
		}
		
		EnchantmentUtils.onTargetDamaged(player, target);
		//TODO target and user damaged should also work when non-player mob attacks (mobs, arrows, trident)
		//TODO damage itemstack
		
		if (target instanceof LivingEntity) {
			float n = targetHealth - ((LivingEntity) target).getHealth();
			if (fireAspect > 0) {
				((LivingEntity) target).setFireForDuration(fireAspect * 4);
			}
			
			//Damage indicator particles
			if (n > 2.0F) {
				int count = (int) ((double) n * 0.5D);
				Position targetPosition = target.getPosition();
				ParticlePacket packet = ParticleCreator.createParticlePacket(
						Particle.DAMAGE_INDICATOR, false,
						targetPosition.getX(), EntityUtils.getBodyY(target, 0.5), targetPosition.getZ(),
						0.1F, 0F, 0.1F,
						0.2F, count, (writer) -> {});
				
				target.sendPacketToViewersAndSelf(packet);
			}
		}
		
		EntityUtils.addExhaustion(player, 0.1F);
	}
}
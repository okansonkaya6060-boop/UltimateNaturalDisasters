package com.emin.und;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;

/**
 * Ultimate Natural Disasters - Forge 1.20.1 prototype.
 * Effects are deliberately bounded and mostly particle/entity based: no large-scale
 * terrain deletion or unbounded block ticking. All world work runs on the server thread.
 */
@Mod(UltimateNaturalDisasters.MOD_ID)
public final class UltimateNaturalDisasters {
    public static final String MOD_ID = "und";

    public UltimateNaturalDisasters() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(DisasterRuntime.class);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        event.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("[UND] Ultimate Natural Disasters yüklendi! /und help"), false);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("und")
                .then(Commands.literal("help").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "UND: /und start <quake_fault|quake_surface|tsunami|tornado|landslide|storm|superstorm|" +
                            "volcano|blizzard|hail|flood|wildfire|meteor> [1-5], /und random, /und stop, /und status"), false);
                    return 1;
                }))
                .then(Commands.literal("start")
                        .then(Commands.argument("disaster", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (DisasterType type : DisasterType.values()) builder.suggest(type.id);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> startFromCommand(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "disaster"), 3))
                                .then(Commands.argument("power", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> startFromCommand(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "disaster"),
                                                IntegerArgumentType.getInteger(ctx, "power"))))))
                .then(Commands.literal("random").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    DisasterType[] types = DisasterType.values();
                    DisasterType type = types[player.getRandom().nextInt(types.length)];
                    return startDisaster(player, type, 2 + player.getRandom().nextInt(4));
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int stopped = DisasterRuntime.stopNear(player.serverLevel(), player.position(), 160);
                    player.sendSystemMessage(Component.literal("[UND] Yakınındaki " + stopped + " afet durduruldu."));
                    return stopped;
                }))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DisasterRuntime.setAuto(player.serverLevel(), true);
                            player.sendSystemMessage(Component.literal("[UND] Otomatik afet sistemi açıldı. Afetler yaklaşık 2-4 dakikada bir başlayabilir."));
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DisasterRuntime.setAuto(player.serverLevel(), false);
                            player.sendSystemMessage(Component.literal("[UND] Otomatik afet sistemi kapatıldı."));
                            return 1;
                        })))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("[UND] Aktif afet: " +
                            DisasterRuntime.countNear(player.serverLevel(), player.position(), 256) +
                            " | Otomatik sistem: " + (DisasterRuntime.isAuto(player.serverLevel()) ? "AÇIK" : "KAPALI")));
                    return 1;
                })));
    }

    private int startFromCommand(ServerPlayer player, String id, int power) {
        DisasterType type = DisasterType.fromId(id);
        if (type == null) {
            player.sendSystemMessage(Component.literal("[UND] Bilinmeyen afet. /und help yaz."));
            return 0;
        }
        return startDisaster(player, type, power);
    }

    private int startDisaster(ServerPlayer player, DisasterType type, int power) {
        DisasterRuntime.start(player.serverLevel(), player.position(), type, power);
        player.sendSystemMessage(Component.literal("[UND] " + type.displayName + " başladı! Şiddet: " + power + "/5"));
        return 1;
    }

    enum DisasterType {
        QUAKE_FAULT("quake_fault", "Fay hattı depremi"),
        QUAKE_SURFACE("quake_surface", "Zemin depremi"),
        TSUNAMI("tsunami", "Tsunami"),
        TORNADO("tornado", "Hortum"),
        LANDSLIDE("landslide", "Heyelan"),
        STORM("storm", "Fırtına"),
        SUPERSTORM("superstorm", "Süper fırtına"),
        VOLCANO("volcano", "Yanardağ patlaması"),
        BLIZZARD("blizzard", "Kar fırtınası"),
        HAIL("hail", "Dolu fırtınası"),
        FLOOD("flood", "Sel"),
        WILDFIRE("wildfire", "Orman yangını"),
        METEOR("meteor", "Meteor yağmuru");

        final String id;
        final String displayName;
        DisasterType(String id, String displayName) { this.id = id; this.displayName = displayName; }
        static DisasterType fromId(String id) {
            for (DisasterType type : values()) if (type.id.equals(id.toLowerCase(Locale.ROOT))) return type;
            return null;
        }
    }

    public static final class DisasterRuntime {
        private static final List<Disaster> ACTIVE = new ArrayList<>();
        private static final Set<ServerLevel> AUTO_LEVELS = Collections.newSetFromMap(new IdentityHashMap<>());
        private static final Map<ServerLevel, Integer> AUTO_COOLDOWNS = new IdentityHashMap<>();
        private static final int MAX_ACTIVE_PER_LEVEL = 4;

        static void setAuto(ServerLevel level, boolean enabled) {
            if (enabled) {
                AUTO_LEVELS.add(level);
                AUTO_COOLDOWNS.put(level, 20 * 30);
            } else {
                AUTO_LEVELS.remove(level);
                AUTO_COOLDOWNS.remove(level);
            }
        }

        static boolean isAuto(ServerLevel level) { return AUTO_LEVELS.contains(level); }

        static void start(ServerLevel level, Vec3 origin, DisasterType type, int power) {
            long sameLevel = ACTIVE.stream().filter(d -> d.level == level).count();
            if (sameLevel >= MAX_ACTIVE_PER_LEVEL) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("[UND] Performans koruması: Bu dünyada zaten 4 aktif afet var."), false);
                return;
            }
            ACTIVE.add(new Disaster(level, origin, type, power));
            level.playSound(null, BlockPos.containing(origin), SoundEvents.WITHER_SPAWN,
                    SoundSource.WEATHER, 0.65F, 0.65F);
        }

        static int stopNear(ServerLevel level, Vec3 pos, double radius) {
            int before = ACTIVE.size();
            ACTIVE.removeIf(d -> d.level == level && d.origin.distanceToSqr(pos) <= radius * radius);
            return before - ACTIVE.size();
        }

        static int countNear(ServerLevel level, Vec3 pos, double radius) {
            return (int) ACTIVE.stream().filter(d -> d.level == level &&
                    d.origin.distanceToSqr(pos) <= radius * radius).count();
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
            if (AUTO_LEVELS.contains(level) && !level.players().isEmpty()) {
                int cooldown = AUTO_COOLDOWNS.getOrDefault(level, 20 * 30) - 1;
                if (cooldown <= 0) {
                    ServerPlayer target = level.players().get(level.getRandom().nextInt(level.players().size()));
                    DisasterType[] choices = DisasterType.values();
                    DisasterType chosen = choices[level.getRandom().nextInt(choices.length)];
                    start(level, target.position(), chosen, 1 + level.getRandom().nextInt(5));
                    AUTO_COOLDOWNS.put(level, 20 * (120 + level.getRandom().nextInt(121)));
                } else AUTO_COOLDOWNS.put(level, cooldown);
            }
            Iterator<Disaster> iterator = ACTIVE.iterator();
            while (iterator.hasNext()) {
                Disaster disaster = iterator.next();
                if (disaster.level != level) continue;
                if (disaster.age++ >= disaster.duration) { iterator.remove(); continue; }
                if (disaster.age % disaster.interval == 0) render(disaster);
                if (disaster.type == DisasterType.TORNADO && disaster.age % 2 == 0) {
                    applyTornadoVortex(disaster);
                    if (disaster.age % 8 == 0) liftNearbyBlocks(disaster);
                } else if (disaster.age % 30 == 0) applyEntityMotion(disaster);
                if (disaster.age == 1) announce(disaster);
            }
        }

        private static void announce(Disaster d) {
            d.level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "[UND] UYARI: " + d.type.displayName + "! Şiddet " + d.power + "/5"), false);
        }

        private static void render(Disaster d) {
            switch (d.type) {
                case QUAKE_FAULT -> renderFaultQuake(d);
                case QUAKE_SURFACE -> renderSurfaceQuake(d);
                case TSUNAMI -> renderTsunami(d);
                case TORNADO -> renderTornado(d);
                case LANDSLIDE -> renderLandslide(d);
                case STORM, SUPERSTORM -> renderStorm(d);
                case VOLCANO -> renderVolcano(d);
                case BLIZZARD -> renderBlizzard(d);
                case HAIL -> renderHail(d);
                case FLOOD -> renderFlood(d);
                case WILDFIRE -> renderWildfire(d);
                case METEOR -> renderMeteor(d);
            }
        }

        private static void renderFaultQuake(Disaster d) {
            // Underground fault trace with surface shock ring: the two quake styles remain distinct.
            double angle = d.angle;
            for (int i = -22; i <= 22; i++) {
                double x = d.origin.x + Math.cos(angle) * i * 1.15;
                double z = d.origin.z + Math.sin(angle) * i * 1.15;
                double y = d.origin.y - 2.2 + Math.sin(i * .5 + d.age * .3) * .35;
                particles(d, ParticleTypes.CRIT, x, y, z, 2, .08, .12, .08, .01);
                if (i % 3 == 0) particles(d, ParticleTypes.CLOUD, x, d.origin.y + .1, z, 1, .12, .08, .12, .01);
            }
            shockRing(d, Math.min(30, d.age * .35), ParticleTypes.CLOUD);
            if (d.age % 90 == 0) sound(d, SoundEvents.GENERIC_EXPLODE, .8F, .55F);
        }

        private static void renderSurfaceQuake(Disaster d) {
            double radius = Math.min(34, d.age * .42);
            shockRing(d, radius, ParticleTypes.POOF);
            for (int i = 0; i < 14; i++) {
                double a = d.random.nextDouble() * Math.PI * 2;
                double r = d.random.nextDouble() * radius;
                particles(d, ParticleTypes.CLOUD, d.origin.x + Math.cos(a) * r,
                        d.origin.y + .1, d.origin.z + Math.sin(a) * r, 2, .3, .08, .3, .01);
            }
            if (d.age % 75 == 0) sound(d, SoundEvents.GENERIC_EXPLODE, .7F, .7F);
        }

        private static void shockRing(Disaster d, double radius, ParticleOptions particle) {
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48.0;
                particles(d, particle, d.origin.x + Math.cos(a) * radius, d.origin.y + .15,
                        d.origin.z + Math.sin(a) * radius, 1, .08, .12, .08, .005);
            }
        }

        private static void renderTsunami(Disaster d) {
            // Moving curved wall of spray. It advances in one direction and pushes entities it reaches.
            double travel = Math.min(48, d.age * (.34 + d.power * .10));
            double width = 17 + d.power * 3;
            for (int i = -18; i <= 18; i++) {
                double side = i * width / 36.0;
                double x = d.origin.x + d.dx * travel - d.dz * side;
                double z = d.origin.z + d.dz * travel + d.dx * side;
                for (int y = 0; y <= 8 + d.power * 2; y += 2) {
                    particles(d, ParticleTypes.SPLASH, x, d.origin.y + y, z, 4, .55, .65, .55, .08);
                    particles(d, ParticleTypes.BUBBLE, x, d.origin.y + y, z, 2, .4, .5, .4, .03);
                }
            }
            if (d.age % 35 == 0) sound(d, SoundEvents.GENERIC_SPLASH, 2.0F, .55F);
        }

        private static Vec3 tornadoCenter(Disaster d) {
            // Hortum yavaşça ilerler; oyuncunun etrafında küçük bir salınım yapar.
            double travel = Math.min(22.0, d.age * (0.025 + d.power * 0.006));
            double drift = Math.sin(d.age * .025) * 4.0;
            return new Vec3(d.origin.x + d.dx * travel + d.dz * drift,
                    d.origin.y, d.origin.z + d.dz * travel - d.dx * drift);
        }

        private static void renderTornado(Disaster d) {
            Vec3 center = tornadoCenter(d);
            for (int y = 0; y < 30; y += 2) {
                double a = d.age * .27 + y * .48;
                // Huni yukarı çıktıkça genişler; alt kısım daha sıkı bir girdap oluşturur.
                double radius = 1.2 + y * .24 + d.power * .30;
                double x = center.x + Math.cos(a) * radius;
                double z = center.z + Math.sin(a) * radius;
                particles(d, ParticleTypes.CLOUD, x, center.y + y, z, 7, .55, .42, .55, .035);
                particles(d, ParticleTypes.POOF, x, center.y + y, z, 2, .28, .28, .28, .025);
                if (y % 6 == 0) {
                    double inner = radius * .55;
                    particles(d, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            center.x + Math.cos(a + 1.2) * inner, center.y + y + .5,
                            center.z + Math.sin(a + 1.2) * inner, 2, .18, .25, .18, .01);
                }
            }
            // Zemindeki toz halkası: girdabın tabana bağlandığını gösterir.
            for (int i = 0; i < 36; i++) {
                double a = Math.PI * 2 * i / 36.0 + d.age * .12;
                double r = 3.5 + d.power * 1.15;
                particles(d, ParticleTypes.CLOUD, center.x + Math.cos(a) * r,
                        center.y + .15, center.z + Math.sin(a) * r, 2, .35, .12, .35, .025);
            }
            if (d.age % 50 == 0) sound(d, SoundEvents.ELYTRA_FLYING, 1.5F, .55F);
        }

        /** Yakındaki güvenli, sıradan blokları gerçek FallingBlockEntity olarak havalandırır. */
        private static void liftNearbyBlocks(Disaster d) {
            int maxBlocks = 8 + d.power * 7;
            if (d.blocksLifted >= maxBlocks) return;
            Vec3 center = tornadoCenter(d);
            for (int attempt = 0; attempt < 18 && d.blocksLifted < maxBlocks; attempt++) {
                double a = d.random.nextDouble() * Math.PI * 2;
                double r = 2.5 + d.random.nextDouble() * (4.0 + d.power * 1.7);
                int x = (int) Math.floor(center.x + Math.cos(a) * r);
                int z = (int) Math.floor(center.z + Math.sin(a) * r);
                int surfaceY = d.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                int y = surfaceY - d.random.nextInt(3);
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = d.level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(d.level, pos) < 0) continue;
                if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL) ||
                        state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.COMMAND_BLOCK) ||
                        state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK) ||
                        state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) continue;
                BlockEntity blockEntity = d.level.getBlockEntity(pos);
                if (blockEntity != null) continue; // Sandık/fırın gibi envanterli bloklara dokunma.

                FallingBlockEntity flying = new FallingBlockEntity(d.level,
                        x + .5, y + .15, z + .5, state);
                if (d.level.removeBlock(pos, false) && d.level.addFreshEntity(flying)) {
                    double tangentX = -(z + .5 - center.z);
                    double tangentZ = (x + .5 - center.x);
                    double length = Math.max(1.0, Math.sqrt(tangentX * tangentX + tangentZ * tangentZ));
                    flying.setDeltaMovement(tangentX / length * (.30 + d.power * .055),
                            .48 + d.random.nextDouble() * .22,
                            tangentZ / length * (.30 + d.power * .055));
                    d.blocksLifted++;
                } else {
                    d.level.setBlock(pos, state, 3);
                }
            }
        }

        /** Girdap, canlıları ve havalanmış blokları merkeze doğru çekerken döndürüp yükseltir. */
        private static void applyTornadoVortex(Disaster d) {
            Vec3 center = tornadoCenter(d);
            double radius = 5.5 + d.power * 2.2;
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                    center.x - radius, center.y - 3, center.z - radius,
                    center.x + radius, center.y + 32, center.z + radius);
            for (Entity entity : d.level.getEntities(null, area)) {
                if (!entity.isAlive()) continue;
                double dx = entity.getX() - center.x;
                double dz = entity.getZ() - center.z;
                double distance = Math.max(.8, Math.sqrt(dx * dx + dz * dz));
                if (distance > radius) continue;
                // Tangansiyel hız = dönüş; merkeze doğru hafif çekim = girdap etkisi.
                double tangentX = -dz / distance;
                double tangentZ = dx / distance;
                double pull = Math.min(.22, .035 + (radius - distance) * .012) * d.power;
                double spin = (.12 + d.power * .035) * (1.0 - Math.min(.65, distance / (radius * 1.8)));
                Vec3 old = entity.getDeltaMovement();
                double lift = entity instanceof FallingBlockEntity ? .075 : .045;
                entity.setDeltaMovement(old.x * .72 + tangentX * spin - dx / distance * pull,
                        Math.min(.62, Math.max(old.y * .55, 0) + lift * d.power),
                        old.z * .72 + tangentZ * spin - dz / distance * pull);
                entity.hurtMarked = true;
            }
        }

        private static void renderLandslide(Disaster d) {
            for (int i = 0; i < 26; i++) {
                double x = d.origin.x + d.random.nextGaussian() * (3 + d.power);
                double z = d.origin.z + d.random.nextGaussian() * (3 + d.power);
                int y = d.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                particles(d, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, y + d.random.nextDouble() * 5, z, 3, .4, .6, .4, .08);
                particles(d, ParticleTypes.CLOUD, x, y, z, 2, .5, .2, .5, .02);
            }
            if (d.age % 55 == 0) sound(d, SoundEvents.DEEPSLATE_BREAK, 1.2F, .65F);
        }

        private static void renderStorm(Disaster d) {
            boolean superStorm = d.type == DisasterType.SUPERSTORM;
            d.level.setWeatherParameters(0, 20 * 60, true, superStorm);
            for (int i = 0; i < (superStorm ? 55 : 28); i++) {
                double x = d.origin.x + d.random.nextGaussian() * 18;
                double z = d.origin.z + d.random.nextGaussian() * 18;
                particles(d, ParticleTypes.CLOUD, x, d.origin.y + 12 + d.random.nextInt(12), z,
                        1, 1.2, .4, 1.2, .01);
                if (superStorm && i % 5 == 0) particles(d, ParticleTypes.ELECTRIC_SPARK, x,
                        d.origin.y + 5, z, 2, .5, 2, .5, .03);
            }
            if (superStorm && d.age % 80 == 0) sound(d, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4F, .7F);
        }

        private static void renderVolcano(Disaster d) {
            for (int i = 0; i < 30 + d.power * 8; i++) {
                double x = d.origin.x + d.random.nextGaussian() * (1.5 + d.power);
                double z = d.origin.z + d.random.nextGaussian() * (1.5 + d.power);
                double y = d.origin.y + 2 + d.random.nextDouble() * (8 + d.power * 4);
                particles(d, ParticleTypes.LAVA, x, y, z, 1, .25, .5, .25, .06);
                particles(d, ParticleTypes.LARGE_SMOKE, x, y + 2, z, 2, .8, .7, .8, .025);
            }
            if (d.age % 45 == 0) sound(d, SoundEvents.FIRE_AMBIENT, 1.4F, .55F);
        }

        private static void renderBlizzard(Disaster d) {
            for (int i = 0; i < 65; i++) {
                double x = d.origin.x + d.random.nextGaussian() * 22;
                double z = d.origin.z + d.random.nextGaussian() * 22;
                particles(d, ParticleTypes.SNOWFLAKE, x, d.origin.y + 5 + d.random.nextInt(16), z,
                        2, 1.2, .6, 1.2, .02);
            }
            d.level.setWeatherParameters(0, 20 * 60, true, false);
        }

        private static void renderHail(Disaster d) {
            for (int i = 0; i < 45; i++) {
                double x = d.origin.x + d.random.nextGaussian() * 17;
                double z = d.origin.z + d.random.nextGaussian() * 17;
                particles(d, ParticleTypes.ITEM_SNOWBALL, x, d.origin.y + 13 + d.random.nextInt(12), z,
                        1, .2, .2, .2, .08);
            }
            if (d.age % 50 == 0) sound(d, SoundEvents.GLASS_HIT, .8F, 1.2F);
        }

        private static void renderFlood(Disaster d) {
            // Rising waterline is represented by dense bubble/splash bands, without flooding chunks automatically.
            double levelY = d.origin.y + Math.min(12, d.age * .12);
            double radius = 5 + d.power * 3;
            for (int i = 0; i < 70; i++) {
                double a = d.random.nextDouble() * Math.PI * 2;
                double r = d.random.nextDouble() * radius;
                particles(d, ParticleTypes.BUBBLE, d.origin.x + Math.cos(a) * r, levelY,
                        d.origin.z + Math.sin(a) * r, 2, .3, .5, .3, .02);
            }
        }

        private static void renderWildfire(Disaster d) {
            for (int i = 0; i < 22 + d.power * 5; i++) {
                double x = d.origin.x + d.random.nextGaussian() * (4 + d.power * 2);
                double z = d.origin.z + d.random.nextGaussian() * (4 + d.power * 2);
                int y = d.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                particles(d, ParticleTypes.FLAME, x, y + d.random.nextDouble() * 2, z, 2, .5, .8, .5, .015);
                particles(d, ParticleTypes.SMOKE, x, y + 2, z, 2, .8, 1, .8, .01);
            }
            if (d.age % 60 == 0) sound(d, SoundEvents.FIRE_AMBIENT, 1.1F, .8F);
        }

        private static void renderMeteor(Disaster d) {
            for (int i = 0; i < 3 + d.power; i++) {
                double x = d.origin.x + d.random.nextGaussian() * 18;
                double z = d.origin.z + d.random.nextGaussian() * 18;
                double y = d.origin.y + 24 - (d.age % 24);
                particles(d, ParticleTypes.FLAME, x, y, z, 5, .35, .8, .35, .04);
                particles(d, ParticleTypes.LAVA, x, y - 1, z, 3, .25, .5, .25, .02);
                particles(d, ParticleTypes.SMOKE, x, y + 2, z, 4, .5, .7, .5, .01);
            }
            if (d.age % 24 == 0) sound(d, SoundEvents.GENERIC_EXPLODE, .8F, .9F);
        }

        private static void applyEntityMotion(Disaster d) {
            double radius = 7 + d.power * 3;
            List<Entity> entities = d.level.getEntities(null,
                    new net.minecraft.world.phys.AABB(d.origin.x - radius, d.origin.y - 8, d.origin.z - radius,
                            d.origin.x + radius, d.origin.y + 30, d.origin.z + radius));
            for (Entity entity : entities) {
                if (entity == null || entity.distanceToSqr(d.origin) > radius * radius) continue;
                double dx = entity.getX() - d.origin.x;
                double dz = entity.getZ() - d.origin.z;
                double distance = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                switch (d.type) {
                    case TSUNAMI, FLOOD -> entity.push(d.dx * .20 * d.power, .08 * d.power, d.dz * .20 * d.power);
                    case QUAKE_FAULT, QUAKE_SURFACE, LANDSLIDE -> {
                        if (entity instanceof LivingEntity living && d.age % 90 == 0)
                            living.knockback(.18 * d.power, dx, dz);
                    }
                    default -> { }
                }
            }
        }

        private static void particles(Disaster d, ParticleOptions type, double x, double y, double z,
                                      int count, double sx, double sy, double sz, double speed) {
            d.level.sendParticles(type, x, y, z, count, sx, sy, sz, speed);
        }

        private static void sound(Disaster d, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            d.level.playSound(null, BlockPos.containing(d.origin), sound, SoundSource.WEATHER, volume, pitch);
        }

        private static final class Disaster {
            final ServerLevel level;
            final Vec3 origin;
            final DisasterType type;
            final int power;
            final RandomSource random;
            final double angle;
            final double dx;
            final double dz;
            final int duration;
            final int interval;
            int age = 0;
            int blocksLifted = 0;

            Disaster(ServerLevel level, Vec3 origin, DisasterType type, int power) {
                this.level = level;
                this.origin = origin;
                this.type = type;
                this.power = power;
                this.random = level.getRandom();
                this.angle = random.nextDouble() * Math.PI * 2;
                this.dx = Math.cos(angle);
                this.dz = Math.sin(angle);
                this.duration = 20 * (type == DisasterType.QUAKE_FAULT || type == DisasterType.QUAKE_SURFACE ? 18 : 35);
                this.interval = type == DisasterType.TORNADO || type == DisasterType.TSUNAMI ? 2 : 5;
            }
        }
    }
}

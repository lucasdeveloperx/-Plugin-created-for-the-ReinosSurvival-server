package LkDeveloper.reinosPerna;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public class ReinosPerna extends JavaPlugin implements Listener {

    private final Map<Player, Integer> playerInjuryTicks = new HashMap<>();
    private final Map<Player, Integer> playerSlownessLevel = new HashMap<>();

    @Override
    public void onEnable() {
        // Carregar a configuração na pasta correta (plugins/ReinosPerna/)
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // Garantir que a seção 'brokenLeg' exista
        if (config.getConfigurationSection("brokenLeg") == null) {
            config.createSection("brokenLeg");
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // Ao iniciar, verificamos o status de perna quebrada de cada jogador
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (config.getBoolean("brokenLeg." + player.getName(), false)) {
                applyInjuryEffects(player);
            }
        }

        getCommand("curarperna").setExecutor((sender, command, label, args) -> {
            if (sender.hasPermission("reinosperna.curar")) {
                if (args.length == 1) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null) {
                        FileConfiguration config1 = getConfig();
                        // Verifica se o jogador tem a perna quebrada no YML
                        if (config1.getBoolean("brokenLeg." + target.getName(), false)) {
                            curePlayer(target);
                            sender.sendMessage("Você curou a perna de " + target.getName());
                        } else {
                            sender.sendMessage(target.getName() + " não está com a perna quebrada.");
                        }
                    } else {
                        sender.sendMessage("Jogador não encontrado.");
                    }
                } else {
                    sender.sendMessage("Use: /curarperna <jogador>");
                }
            } else {
                sender.sendMessage("Você não tem permissão para usar este comando.");
            }
            return true;
        });

        // Atualização do tempo de lesão a cada tick
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : playerInjuryTicks.keySet()) {
                    int ticks = playerInjuryTicks.get(player);
                    if (ticks < 600) {
                        playerInjuryTicks.put(player, ticks + 1);
                    } else if (ticks < 1200) {
                        // Após 30 segundos (600 ticks), aumenta o nível de Slowness para 3
                        applySlowness(player, 3);
                        playerInjuryTicks.put(player, ticks + 1);
                    } else if (ticks < 1800) {
                        // Após 60 segundos (1200 ticks), aumenta o nível de Slowness para 4
                        applySlowness(player, 4);
                        playerInjuryTicks.put(player, ticks + 1);
                    } else if (ticks >= 1800) {
                        // Após 90 segundos (1800 ticks), mantém o Slowness 4 indefinidamente
                        applySlowness(player, 4);
                    }
                }
            }
        }.runTaskTimer(this, 20, 1); // 20 ticks = 1 segundo, então executa a cada tick
    }

    @EventHandler
    public void onPlayerFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (event.getCause() == DamageCause.FALL) {
                double fallHeight = player.getFallDistance();

                if (fallHeight > 7) {  // A altura mínima para causar a quebra de perna (agora 7 blocos)
                    event.setDamage(0);  // Anula o dano, pois o efeito da perna quebrada será tratado separadamente
                    player.sendTitle("§cVocê quebrou a perna!", "", 10, 70, 20);  // Título vermelho
                    applyInjuryEffects(player);

                    // Armazena o jogador como tendo a perna quebrada no YML
                    getConfig().set("brokenLeg." + player.getName(), true);
                    saveConfig();

                    // Reduz a vida do jogador em 6 corações
                    player.setHealth(player.getHealth() - 12.0); // Cada coração é 2 pontos de saúde

                    // Toca um som de quebra de perna
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

                    // Gera a partícula de sangue (dust vermelha) no jogador
                    spawnBloodParticles(player);
                }
            }
        }
    }

    private void applyInjuryEffects(Player player) {
        // Aplica Slowness 3 indefinido
        applySlowness(player, 3);  // Aplica Slowness 3 indefinidamente
        playerInjuryTicks.put(player, 0);  // Inicia o contador de tempo para aumentar o Slowness

        // Impede o jogador de tomar leite se estiver com a perna quebrada
        getConfig().set("brokenLeg." + player.getName(), true);
        saveConfig();
    }

    private void applySlowness(Player player, int slownessLevel) {
        if (!playerSlownessLevel.containsKey(player) || playerSlownessLevel.get(player) < slownessLevel) {
            // Remove os efeitos anteriores de Slowness
            player.removePotionEffect(PotionEffectType.SLOW);

            // Aplica o novo nível de Slowness com base na duração do efeito
            int duration = Integer.MAX_VALUE; // Slowness 3 com duração infinita
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, slownessLevel - 1)); // slownessLevel - 1 pois o nível é baseado em 0
            playerSlownessLevel.put(player, slownessLevel);
        }
    }

    private void spawnBloodParticles(Player player) {
        // Gera partículas de "dust" vermelhas (simulando sangue)
        World world = player.getWorld();
        world.spawnParticle(Particle.REDSTONE, player.getLocation(), 50, 1.0, 1.0, 1.0, 0, new Particle.DustOptions(Color.RED, 1));
    }

    private void curePlayer(Player player) {
        // Cura o jogador, remove o efeito de Slowness e restaura a velocidade
        player.removePotionEffect(PotionEffectType.SLOW);
        player.setWalkSpeed(0.2f); // Velocidade padrão
        player.sendMessage("Sua perna foi curada!");

        // Restaura os 6 corações perdidos
        player.setHealth(Math.min(player.getHealth() + 12.0, player.getMaxHealth()));

        playerInjuryTicks.remove(player);
        playerSlownessLevel.remove(player); // Remove o nível de Slowness

        // Remove o jogador do YML quando curado
        getConfig().set("brokenLeg." + player.getName(), null);
        saveConfig();

        // Toca um som de cura
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerUseMilk(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType().name().contains("MILK_BUCKET")) {
            // Impede o uso de leite se o jogador estiver com a perna quebrada
            if (playerSlownessLevel.containsKey(player) && playerSlownessLevel.get(player) >= 3) {
                event.setCancelled(true);
                player.sendMessage("Você não pode tomar leite enquanto está com a perna quebrada!");
            }
        }
    }

    @Override
    public void onDisable() {
        // Limpar qualquer recurso se necessário
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = getConfig();

        // Verifica se o jogador tinha a perna quebrada antes de entrar
        if (config.getBoolean("brokenLeg." + player.getName(), false)) {
            applyInjuryEffects(player);
            player.sendMessage("Você ainda está com a perna quebrada.");
        }
    }
}

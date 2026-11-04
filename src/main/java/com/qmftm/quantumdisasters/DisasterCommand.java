package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.disaster.Disaster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DisasterCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("start", "stop", "skip", "trigger", "list", "status", "reload");

    private final QuantumDisasters plugin;

    public DisasterCommand(QuantumDisasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DisasterManager manager = plugin.disasterManager();
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (manager.start()) {
                    ok(sender, "자연재해 게임을 시작했습니다.");
                } else {
                    err(sender, "이미 진행 중입니다.");
                }
            }
            case "stop" -> {
                if (manager.stop()) {
                    ok(sender, "자연재해 게임을 멈췄습니다.");
                } else {
                    err(sender, "진행 중인 게임이 없습니다.");
                }
            }
            case "skip" -> {
                if (manager.skip()) {
                    ok(sender, "현재 단계를 건너뛰었습니다.");
                } else {
                    err(sender, "진행 중인 게임이 없습니다.");
                }
            }
            case "trigger" -> {
                if (args.length < 2) {
                    err(sender, "사용법: /" + label + " trigger <재해>");
                    return true;
                }
                Disaster disaster = manager.disasters().get(args[1].toLowerCase(Locale.ROOT));
                if (disaster == null) {
                    err(sender, "알 수 없는 재해입니다. /" + label + " list 로 목록을 확인하세요.");
                    return true;
                }
                manager.trigger(disaster);
                sender.sendMessage(Component.text("[재해] ", NamedTextColor.GREEN)
                        .append(disaster.displayName())
                        .append(Component.text(" 을(를) 발생시켰습니다.", NamedTextColor.WHITE)));
            }
            case "list" -> {
                sender.sendMessage(Component.text("[재해] 재해 목록", NamedTextColor.GOLD));
                for (Disaster d : manager.disasters().values()) {
                    sender.sendMessage(Component.text(" - " + d.id() + " ", NamedTextColor.GRAY)
                            .append(d.displayName())
                            .append(Component.text(d.enabled() ? " (켜짐)" : " (꺼짐)",
                                    d.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
                }
            }
            case "status" -> {
                String state = switch (manager.state()) {
                    case STOPPED -> "멈춤";
                    case WAITING -> "휴식 중 (다음 재해 대기)";
                    case ACTIVE -> "재해 진행 중";
                };
                Component msg = Component.text("[재해] 상태: " + state, NamedTextColor.YELLOW);
                if (manager.isRunning()) {
                    msg = msg.append(Component.text(" / DAY " + manager.day(), NamedTextColor.GOLD));
                }
                if (manager.periodDisaster() != null) {
                    msg = msg.append(Component.text(" / 이번 재해: ", NamedTextColor.GRAY)).append(manager.periodDisaster().displayName());
                }
                sender.sendMessage(msg);
            }
            case "reload" -> {
                plugin.reloadConfig();
                ok(sender, "설정을 다시 불러왔습니다.");
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            options.addAll(plugin.disasterManager().disasters().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.startsWith(prefix));
        return options;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("[재해] 명령어", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" /" + label + " start - 게임 시작 (주기적으로 재해 발생)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " stop - 게임 종료", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " skip - 현재 단계 건너뛰기", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " trigger <재해> - 특정 재해 즉시 발생", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " list - 재해 목록", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " status - 현재 상태", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " reload - 설정 다시 불러오기", NamedTextColor.GRAY));
    }

    private static void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[재해] " + message, NamedTextColor.GREEN));
    }

    private static void err(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[재해] " + message, NamedTextColor.RED));
    }
}

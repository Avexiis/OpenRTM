package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.games.ActionContext;
import openrtm.games.GameButton;
import openrtm.games.GameDefinition;
import openrtm.games.GameToggle;
import openrtm.games.StatField;
import openrtm.games.StatPreset;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class GamePagePanel extends JPanel {
    private static final Color LINE = new Color(55, 60, 66);
    private static final Color ACCENT = new Color(91, 141, 239);

    private final ConsoleService service;
    private final GameDefinition game;
    private final TaskRunner tasks;
    private final Map<String, JTextField> inputs = new LinkedHashMap<>();
    private final Map<String, JTextField> statInputs = new LinkedHashMap<>();
    private JComboBox<String> mapBox;
    private JComboBox<String> gametypeBox;
    private JSpinner clientSpinner;
    private JCheckBox allClientsCheck;
    private JList<String> clientList;

    public GamePagePanel(ConsoleService service, GameDefinition game, TaskRunner tasks) {
        super(new BorderLayout(10, 10));
        this.service = service;
        this.game = game;
        this.tasks = tasks;
        build();
    }

    private void build() {
        setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel name = new JLabel(game.name() + "  " + game.titleId());
        name.setForeground(ACCENT);
        title.add(name);
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        JPanel controls = sectionGrid();
        if (hasSetupInputs()) controls.add(inputsSection());
        if (game.clients() != null) controls.add(clientsSection());
        addGroupedButtons(controls, group -> !group.equals("Recovery") && !isZombiesGroup(group));
        if (controls.getComponentCount() == 0) controls.add(limitedSection());
        tabs.addTab("Controls", controls);

        JPanel patches = sectionGrid();
        addGroupedToggles(patches, group -> !group.equals("Recovery") && !isZombiesGroup(group));
        if (patches.getComponentCount() > 0) tabs.addTab("Patches", patches);

        JPanel recovery = sectionGrid();
        addGroupedButtons(recovery, group -> group.equals("Recovery"));
        addGroupedToggles(recovery, group -> group.equals("Recovery"));
        if (!game.statFields().isEmpty()) recovery.add(statsSection());
        if (recovery.getComponentCount() > 0) tabs.addTab("Recovery", recovery);

        JPanel zombies = sectionGrid();
        addGroupedButtons(zombies, this::isZombiesGroup);
        addGroupedToggles(zombies, this::isZombiesGroup);
        if (zombies.getComponentCount() > 0) tabs.addTab("Zombies", zombies);

        add(tabs, BorderLayout.CENTER);
    }

    private JPanel inputsSection() {
        JPanel panel = section("Setup");
        JPanel grid = new JPanel(new GridLayout(0, 6, 6, 6));

        if (!game.maps().isEmpty()) {
            mapBox = new JComboBox<>(game.maps().keySet().toArray(String[]::new));
            addLabeled(grid, "Map", mapBox);
        }
        if (!game.gametypes().isEmpty()) {
            gametypeBox = new JComboBox<>(game.gametypes().keySet().toArray(String[]::new));
            addLabeled(grid, "Mode", gametypeBox);
        }

        if (hasButton("Set FOV")) field(grid, "fov", "FOV", "90");
        if (hasButton("Switch Team") || hasButton("Change Team")) field(grid, "team", "Team", "allies");
        if (usesGamertagInput()) field(grid, "gamertag", "Gamertag", "");
        if (hasButton("Set Clan Tag")) field(grid, "clantag", "Clan Tag", "");
        if (hasButton("Spoof IP")) field(grid, "ip", "IP Spoof", "");
        if (hasButton("3D Name")) {
            field(grid, "line1", "3D Name Top", "^1OpenRTM");
            field(grid, "line2", "3D Name Bottom", "^2Xbox 360");
        }
        if (usesMessageInput()) field(grid, "message", "Message", "OpenRTM");
        if (hasButton("Send Game Command")) field(grid, "raw", "Game Command", "");
        if (hasButton("Send Server Command")) field(grid, "serverCommand", "Server Command", "");
        if (hasButton("Send Zombies Command")) field(grid, "zombieCommand", "Zombies Command", "");

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel clientsSection() {
        JPanel panel = section("Clients");
        JPanel top = row(FlowLayout.LEFT);
        int max = Math.max(0, game.clients().maxClients() - 1);
        clientSpinner = new JSpinner(new SpinnerNumberModel(0, 0, max, 1));
        allClientsCheck = new JCheckBox(game.name().equals("Call of Duty 4") ? "Selected stat call" : "All clients");
        JButton refresh = button("Refresh Clients");
        refresh.addActionListener(e -> refreshClients());
        top.add(new JLabel("Client"));
        top.add(clientSpinner);
        top.add(allClientsCheck);
        top.add(refresh);
        panel.add(top, BorderLayout.NORTH);

        clientList = new JList<>(new String[]{"Refresh to read clients"});
        JScrollPane scroll = new JScrollPane(clientList);
        scroll.setPreferredSize(new Dimension(280, 90));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statsSection() {
        JPanel panel = section("Stats");
        JPanel grid = new JPanel(new GridLayout(0, 6, 6, 6));
        for (StatField field : game.statFields()) {
            JTextField text = new JTextField(field.defaultValue(), 9);
            statInputs.put(field.key(), text);
            addLabeled(grid, field.label(), text);
        }
        panel.add(grid, BorderLayout.CENTER);

        JPanel actions = row(FlowLayout.LEFT);
        if (!game.presets().isEmpty()) {
            JComboBox<String> presetBox = new JComboBox<>(game.presets().stream().map(StatPreset::name).toArray(String[]::new));
            JButton preset = button("Load Preset");
            preset.addActionListener(e -> loadPreset((String) presetBox.getSelectedItem()));
            actions.add(presetBox);
            actions.add(preset);
        }
        if (game.statsApplier() != null) {
            JButton apply = button("Apply Stats");
            apply.addActionListener(e -> tasks.run(game.name() + " apply stats", () -> game.statsApplier().apply(service, context())));
            actions.add(apply);
        }
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel limitedSection() {
        JPanel limited = section("Reference");
        limited.add(new JLabel("No title-specific actions have been ported yet."), BorderLayout.NORTH);
        return limited;
    }

    private void addGroupedButtons(JPanel content, Predicate<String> includeGroup) {
        Map<String, List<GameButton>> grouped = new LinkedHashMap<>();
        for (GameButton button : game.buttons()) {
            if (includeGroup.test(button.group())) {
                grouped.computeIfAbsent(button.group(), k -> new java.util.ArrayList<>()).add(button);
            }
        }
        for (Map.Entry<String, List<GameButton>> entry : grouped.entrySet()) {
            JPanel panel = section(entry.getKey());
            JPanel grid = new JPanel(new GridLayout(0, 4, 6, 6));
            for (GameButton action : entry.getValue()) {
                JButton button = button(action.label());
                button.addActionListener(e -> tasks.run(game.name() + " " + action.label(), () -> action.runner().run(context())));
                grid.add(button);
            }
            panel.add(grid, BorderLayout.CENTER);
            content.add(panel);
        }
    }

    private void addGroupedToggles(JPanel content, Predicate<String> includeGroup) {
        Map<String, List<GameToggle>> grouped = new LinkedHashMap<>();
        for (GameToggle toggle : game.toggles()) {
            if (includeGroup.test(toggle.group())) {
                grouped.computeIfAbsent(toggle.group(), k -> new java.util.ArrayList<>()).add(toggle);
            }
        }
        for (Map.Entry<String, List<GameToggle>> entry : grouped.entrySet()) {
            JPanel panel = section(entry.getKey());
            JPanel grid = new JPanel(new GridLayout(0, 4, 6, 6));
            for (GameToggle toggle : entry.getValue()) {
                JCheckBox check = new JCheckBox(toggle.label());
                check.addActionListener(e -> tasks.run(game.name() + " " + toggle.label(), () -> {
                    if (check.isSelected()) toggle.enable().run(context());
                    else toggle.disable().run(context());
                }));
                grid.add(check);
            }
            panel.add(grid, BorderLayout.CENTER);
            content.add(panel);
        }
    }

    private boolean hasSetupInputs() {
        return !game.maps().isEmpty()
                || !game.gametypes().isEmpty()
                || hasButton("Set FOV")
                || hasButton("Switch Team")
                || hasButton("Change Team")
                || usesGamertagInput()
                || hasButton("Set Clan Tag")
                || hasButton("Spoof IP")
                || hasButton("3D Name")
                || usesMessageInput()
                || hasButton("Send Game Command")
                || hasButton("Send Server Command")
                || hasButton("Send Zombies Command");
    }

    private boolean usesGamertagInput() {
        return game.buttons().stream()
                .map(GameButton::label)
                .anyMatch(label -> label.contains("Gamertag"));
    }

    private boolean usesMessageInput() {
        return game.buttons().stream()
                .map(GameButton::label)
                .anyMatch(label -> label.contains("Message") || label.contains("MOTD"));
    }

    private boolean hasButton(String label) {
        return game.buttons().stream().anyMatch(button -> button.label().equals(label));
    }

    private boolean isZombiesGroup(String group) {
        return group.startsWith("Zombies");
    }

    private ActionContext context() {
        Map<String, String> values = new LinkedHashMap<>();
        inputs.forEach((key, field) -> values.put(key, field.getText()));
        statInputs.forEach((key, field) -> values.put(key, field.getText()));
        String map = "";
        if (mapBox != null && mapBox.getSelectedItem() != null) {
            map = game.maps().getOrDefault(mapBox.getSelectedItem().toString(), "");
        }
        String mode = "";
        if (gametypeBox != null && gametypeBox.getSelectedItem() != null) {
            mode = game.gametypes().getOrDefault(gametypeBox.getSelectedItem().toString(), "");
        }
        int clientIndex = clientSpinner == null ? 0 : ((Number) clientSpinner.getValue()).intValue();
        boolean allClients = allClientsCheck != null && allClientsCheck.isSelected();
        return new ActionContext(service, game, values, clientIndex, allClients, map, mode);
    }

    private void refreshClients() {
        tasks.run(game.name() + " refresh clients", () -> {
            List<String> clients = openrtm.games.GameActions.readClients(service, game.clients());
            SwingUtilities.invokeLater(() -> clientList.setListData(clients.toArray(String[]::new)));
        });
    }

    private void loadPreset(String name) {
        for (StatPreset preset : game.presets()) {
            if (preset.name().equals(name)) {
                preset.values().forEach((key, value) -> {
                    JTextField field = statInputs.get(key);
                    if (field != null) field.setText(value);
                });
                return;
            }
        }
    }

    private JPanel sectionGrid() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        return panel;
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        JLabel label = new JLabel(title);
        label.setForeground(ACCENT);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    private static JPanel row(int align) {
        JPanel panel = new JPanel(new FlowLayout(align, 6, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private void field(JPanel grid, String key, String label, String value) {
        JTextField text = new JTextField(value, 10);
        inputs.put(key, text);
        addLabeled(grid, label, text);
    }

    private static void addLabeled(JPanel grid, String label, Component component) {
        grid.add(new JLabel(label));
        grid.add(component);
    }

    private static JButton button(String label) {
        return new JButton(label);
    }
}

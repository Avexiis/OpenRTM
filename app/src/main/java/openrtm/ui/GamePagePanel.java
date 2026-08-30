package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.games.ActionContext;
import openrtm.games.GameButton;
import openrtm.games.GameDefinition;
import openrtm.games.GameToggle;
import openrtm.games.StatField;
import openrtm.games.StatPreset;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class GamePagePanel extends JPanel {
    private static final Color LINE = new Color(55, 60, 66);
    private static final Color ACCENT = new Color(91, 141, 239);
    private static final int FORM_COLUMNS = 3;
    private static final int ACTION_COLUMNS = 3;
    private static final Dimension FIELD_SIZE = new Dimension(130, 28);
    private static final Dimension BUTTON_SIZE = new Dimension(160, 30);

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

        WidthTrackingPanel content = new WidthTrackingPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(2, 0, 8, 4));

        boolean added = false;
        if (hasSetupInputs()) {
            addSection(content, inputsSection());
            added = true;
        }

        JPanel leftColumn = stackPanel();
        JPanel actionColumn = stackPanel();
        boolean leftAdded = false;
        boolean actionAdded = false;
        if (game.clients() != null) {
            addSection(leftColumn, clientsSection());
            leftAdded = true;
        }
        actionAdded |= addGroupedButtons(actionColumn, group -> !group.equals("Recovery") && !isZombiesGroup(group));
        actionAdded |= addGroupedToggles(actionColumn, group -> !group.equals("Recovery") && !isZombiesGroup(group));
        if (leftAdded || actionAdded) {
            addBodyColumns(content, leftColumn, leftAdded, actionColumn, actionAdded);
            added = true;
        }

        added |= addGroupedButtons(content, group -> group.equals("Recovery"));
        added |= addGroupedToggles(content, group -> group.equals("Recovery"));
        if (!game.statFields().isEmpty()) {
            addSection(content, statsSection());
            added = true;
        }
        added |= addGroupedButtons(content, this::isZombiesGroup);
        added |= addGroupedToggles(content, this::isZombiesGroup);

        if (!added) {
            addSection(content, limitedSection());
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel inputsSection() {
        JPanel panel = section("Setup");
        JPanel form = formGrid();
        int index = 0;

        if (!game.maps().isEmpty()) {
            mapBox = new JComboBox<>(game.maps().keySet().toArray(String[]::new));
            sizeField(mapBox);
            index = addFormItem(form, index, "Map", mapBox);
        }
        if (!game.gametypes().isEmpty()) {
            gametypeBox = new JComboBox<>(game.gametypes().keySet().toArray(String[]::new));
            sizeField(gametypeBox);
            index = addFormItem(form, index, "Mode", gametypeBox);
        }

        if (hasButton("Set FOV")) index = field(form, index, "fov", "FOV", "90");
        if (hasButton("Switch Team") || hasButton("Change Team")) index = field(form, index, "team", "Team", "allies");
        if (usesGamertagInput()) index = field(form, index, "gamertag", "Gamertag", "");
        if (hasButton("Set Clan Tag")) index = field(form, index, "clantag", "Clan Tag", "");
        if (hasButton("Spoof IP")) index = field(form, index, "ip", "IP Spoof", "");
        if (hasButton("3D Name")) {
            index = field(form, index, "line1", "3D Name Top", "^1OpenRTM");
            index = field(form, index, "line2", "3D Name Bottom", "^2Xbox 360");
        }
        if (usesMessageInput()) index = field(form, index, "message", "Message", "OpenRTM");
        if (hasButton("Send Game Command")) index = field(form, index, "raw", "Game Command", "");
        if (hasButton("Send Server Command")) index = field(form, index, "serverCommand", "Server Command", "");
        if (hasButton("Send Zombies Command")) index = field(form, index, "zombieCommand", "Zombies Command", "");

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private JPanel clientsSection() {
        JPanel panel = section("Clients");
        JPanel controls = row(FlowLayout.LEFT);
        int max = Math.max(0, game.clients().maxClients() - 1);
        clientSpinner = new JSpinner(new SpinnerNumberModel(0, 0, max, 1));
        clientSpinner.setPreferredSize(new Dimension(64, 28));
        allClientsCheck = new JCheckBox(game.name().equals("Call of Duty 4") ? "Selected client stats" : "All clients");
        JButton refresh = button("Refresh Clients");
        refresh.addActionListener(e -> refreshClients());
        controls.add(new JLabel("Client"));
        controls.add(clientSpinner);
        controls.add(allClientsCheck);
        controls.add(refresh);
        panel.add(controls, BorderLayout.NORTH);

        clientList = new JList<>(new String[]{"Refresh to read clients"});
        clientList.setVisibleRowCount(5);
        JScrollPane scroll = new JScrollPane(clientList);
        scroll.setPreferredSize(new Dimension(320, 112));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statsSection() {
        JPanel panel = section("Stats");
        JPanel form = formGrid();
        int index = 0;
        for (StatField field : game.statFields()) {
            JTextField text = new JTextField(field.defaultValue(), 9);
            sizeField(text);
            statInputs.put(field.key(), text);
            index = addFormItem(form, index, field.label(), text);
        }
        panel.add(form, BorderLayout.CENTER);

        JPanel actions = row(FlowLayout.LEFT);
        if (!game.presets().isEmpty()) {
            JComboBox<String> presetBox = new JComboBox<>(game.presets().stream().map(StatPreset::name).toArray(String[]::new));
            sizeField(presetBox);
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
        limited.add(new JLabel("No title-specific actions have been ported yet."), BorderLayout.CENTER);
        return limited;
    }

    private boolean addGroupedButtons(JPanel content, Predicate<String> includeGroup) {
        Map<String, List<GameButton>> grouped = new LinkedHashMap<>();
        for (GameButton button : game.buttons()) {
            if (includeGroup.test(button.group())) {
                grouped.computeIfAbsent(button.group(), k -> new java.util.ArrayList<>()).add(button);
            }
        }
        for (Map.Entry<String, List<GameButton>> entry : grouped.entrySet()) {
            JPanel panel = section(entry.getKey());
            JPanel grid = actionGrid();
            int index = 0;
            for (GameButton action : entry.getValue()) {
                JButton button = button(action.label());
                button.addActionListener(e -> tasks.run(game.name() + " " + action.label(), () -> action.runner().run(context())));
                addActionComponent(grid, button, index++);
            }
            panel.add(grid, BorderLayout.WEST);
            addSection(content, panel);
        }
        return !grouped.isEmpty();
    }

    private boolean addGroupedToggles(JPanel content, Predicate<String> includeGroup) {
        Map<String, List<GameToggle>> grouped = new LinkedHashMap<>();
        for (GameToggle toggle : game.toggles()) {
            if (includeGroup.test(toggle.group())) {
                grouped.computeIfAbsent(toggle.group(), k -> new java.util.ArrayList<>()).add(toggle);
            }
        }
        for (Map.Entry<String, List<GameToggle>> entry : grouped.entrySet()) {
            JPanel panel = section(entry.getKey());
            JPanel grid = actionGrid();
            int index = 0;
            for (GameToggle toggle : entry.getValue()) {
                JCheckBox check = new JCheckBox(toggle.label());
                sizeAction(check);
                check.addActionListener(e -> tasks.run(game.name() + " " + toggle.label(), () -> {
                    if (check.isSelected()) toggle.enable().run(context());
                    else toggle.disable().run(context());
                }));
                addActionComponent(grid, check, index++);
            }
            panel.add(grid, BorderLayout.WEST);
            addSection(content, panel);
        }
        return !grouped.isEmpty();
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

    private void addSection(JPanel content, JPanel section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = section.getPreferredSize();
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        content.add(section);
        content.add(Box.createVerticalStrut(4));
    }

    private void addBodyColumns(JPanel content, JPanel leftColumn, boolean leftAdded, JPanel actionColumn, boolean actionAdded) {
        JPanel body = new JPanel(new GridBagLayout());
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (leftAdded && actionAdded) {
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = 0;
            left.weightx = 0.32;
            left.fill = GridBagConstraints.HORIZONTAL;
            left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(0, 0, 0, 12);
            body.add(leftColumn, left);

            GridBagConstraints right = new GridBagConstraints();
            right.gridx = 1;
            right.gridy = 0;
            right.weightx = 0.68;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.anchor = GridBagConstraints.NORTHWEST;
            body.add(actionColumn, right);
        } else {
            GridBagConstraints only = new GridBagConstraints();
            only.gridx = 0;
            only.gridy = 0;
            only.weightx = 1.0;
            only.fill = GridBagConstraints.HORIZONTAL;
            only.anchor = GridBagConstraints.NORTHWEST;
            body.add(leftAdded ? leftColumn : actionColumn, only);
        }

        Dimension preferred = body.getPreferredSize();
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        content.add(body);
        content.add(Box.createVerticalStrut(4));
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(8, 0, 6, 0)));
        JLabel label = new JLabel(title);
        label.setForeground(ACCENT);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    private JPanel formGrid() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel actionGrid() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel stackPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private int field(JPanel grid, int index, String key, String label, String value) {
        JTextField text = new JTextField(value, 10);
        sizeField(text);
        inputs.put(key, text);
        return addFormItem(grid, index, label, text);
    }

    private static int addFormItem(JPanel grid, int index, String label, JComponent component) {
        int row = index / FORM_COLUMNS;
        int column = (index % FORM_COLUMNS) * 2;

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = column;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.EAST;
        labelConstraints.insets = new Insets(0, 0, 6, 4);
        grid.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = column + 1;
        fieldConstraints.gridy = row;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.insets = new Insets(0, 0, 6, column + 1 == FORM_COLUMNS * 2 - 1 ? 0 : 12);
        grid.add(component, fieldConstraints);
        return index + 1;
    }

    private static void addActionComponent(JPanel grid, JComponent component, int index) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = index % ACTION_COLUMNS;
        constraints.gridy = index / ACTION_COLUMNS;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 6, 6);
        grid.add(component, constraints);
    }

    private static JPanel row(int align) {
        JPanel panel = new JPanel(new FlowLayout(align, 6, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JButton button(String label) {
        JButton button = new JButton(label);
        sizeAction(button);
        return button;
    }

    private static void sizeAction(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(BUTTON_SIZE.width, preferred.width), BUTTON_SIZE.height));
    }

    private static void sizeField(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(Math.max(FIELD_SIZE.width, preferred.width), FIELD_SIZE.height));
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(18, visibleRect.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UniversityITAssetsUI {

    // Конфигурация подключения к БД. 
    // Рекомендуется использовать переменные окружения или config.properties для продакшена.
    private static final String DB_URL = "jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=UniversityITAssets;encrypt=false;trustServerCertificate=true;integratedSecurity=true;";

    private JFrame frame;
    private JTable assetsTable;
    private DefaultTableModel assetsTableModel;
    private JTextArea detailsArea;

    // Вспомогательные классы для элементов выпадающих списков
    private static class LocationItem {
        int id;
        String label;
        LocationItem(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    private static class EmployeeItem {
        int id;
        String label;
        EmployeeItem(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }

    // Рендерер кнопки удаления в таблице
    private static class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() { setOpaque(true); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText("🗑");
            setToolTipText("Удалить актив");
            return this;
        }
    }

    // Редактор кнопки удаления с подтверждением действия
    private class ButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private int assetId;
        private boolean isPushed;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> {
                if (isPushed) confirmAndDelete(assetId);
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            assetId = (int) table.getModel().getValueAt(row, 0);
            button.setText("🗑");
            isPushed = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            isPushed = false;
            return new Object();
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }

        private void confirmAndDelete(int assetId) {
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Вы уверены, что хотите удалить актив с ID = " + assetId + "?\nВсе связанные данные будут удалены.",
                    "Подтверждение удаления", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                try (Connection conn = getConnection();
                     CallableStatement stmt = conn.prepareCall("{CALL Delete_Asset_Safe(?)}")) {
                    stmt.setInt(1, assetId);
                    stmt.execute();
                    JOptionPane.showMessageDialog(frame, "✅ Актив удален", "Успех", JOptionPane.INFORMATION_MESSAGE);
                    loadAssets("Все");
                } catch (SQLException ex) {
                    showError("Ошибка удаления", ex);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
        } catch (Exception e) { e.printStackTrace(); }
        
        SwingUtilities.invokeLater(() -> new UniversityITAssetsUI().createAndShowGUI());
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void createAndShowGUI() {
        frame = new JFrame("🏛 Университет — Учёт IT-Активов");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1150, 650);
        frame.setLocationRelativeTo(null);

        String[] columns = {"ID", "Инв №", "Тип", "Модель", "Статус", "Место", "Ответственный", "Действия"};
        assetsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 7;
            }
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 7) return JButton.class;
                return String.class;
            }
        };

        assetsTable = new JTable(assetsTableModel);
        assetsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assetsTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        assetsTable.getSelectionModel().addListSelectionListener(e -> showAssetDetails());
        
        assetsTable.setDefaultRenderer(JButton.class, new ButtonRenderer());
        assetsTable.setDefaultEditor(JButton.class, new ButtonEditor(new JCheckBox()));
        setupInlineEditing();

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{"Все", "active", "in_repair", "decommissioned"});
        JButton refreshBtn = new JButton("⟳ Обновить");
        JButton addBtn = new JButton("➕ Добавить");
        JButton reportBtn = new JButton("📊 Отчёт по замене");

        topPanel.add(new JLabel("Статус:"));
        topPanel.add(statusFilter);
        topPanel.add(refreshBtn);
        topPanel.add(addBtn);
        topPanel.add(reportBtn);

        refreshBtn.addActionListener(e -> loadAssets((String) statusFilter.getSelectedItem()));
        addBtn.addActionListener(e -> showAddAssetDialog());
        reportBtn.addActionListener(e -> showReplacementReport());
        statusFilter.addActionListener(e -> loadAssets((String) statusFilter.getSelectedItem()));

        detailsArea = new JTextArea(5, 50);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setBorder(BorderFactory.createTitledBorder("Подробности актива"));

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(assetsTable), BorderLayout.CENTER);
        frame.add(new JScrollPane(detailsArea), BorderLayout.SOUTH);

        frame.setVisible(true);
        loadAssets("Все");
    }

    private void setupInlineEditing() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("✏️ Изменить поле");
        popupMenu.add(editItem);

        editItem.addActionListener(e -> {
            int row = assetsTable.getSelectedRow();
            int col = assetsTable.getSelectedColumn();
            if (row == -1 || col == 0 || col == 7) return;

            int assetId = (int) assetsTableModel.getValueAt(row, 0);
            String columnName = assetsTable.getColumnName(col);
            Object oldValue = assetsTableModel.getValueAt(row, col);

            try {
                Object newValue = promptForValue(columnName, oldValue);
                if (newValue == null) return;
                updateDatabaseField(assetId, columnName, newValue, row, col);
            } catch (Exception ex) {
                showError("Ошибка обновления", new SQLException(ex.getMessage()));
            }
        });

        assetsTable.setComponentPopupMenu(popupMenu);
    }

    private void updateDatabaseField(int assetId, String columnName, Object newValue, int row, int col) throws SQLException {
        String dbColumn;
        switch (columnName) {
            case "Инв №" -> dbColumn = "inventory_number";
            case "Тип" -> dbColumn = "asset_type";
            case "Модель" -> dbColumn = "model";
            case "Статус" -> dbColumn = "status";
            case "Место" -> dbColumn = "current_location_id";
            case "Ответственный" -> dbColumn = "responsible_employee_id";
            default -> throw new IllegalArgumentException("Поле не поддерживается");
        }

        String sql = "UPDATE Assets SET " + dbColumn + " = ? WHERE asset_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (dbColumn.equals("current_location_id") || dbColumn.equals("responsible_employee_id")) {
                if (newValue instanceof LocationItem loc) stmt.setInt(1, loc.id == -1 ? 0 : loc.id);
                else if (newValue instanceof EmployeeItem emp) stmt.setInt(1, emp.id == -1 ? 0 : emp.id);
                else stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setString(1, newValue.toString());
            }
            stmt.setInt(2, assetId);

            if (stmt.executeUpdate() > 0) {
                String displayVal = newValue instanceof LocationItem l ? l.label : 
                                    newValue instanceof EmployeeItem e ? e.label : newValue.toString();
                assetsTableModel.setValueAt(displayVal, row, col);
                showAssetDetails();
            }
        }
    }

    private Object promptForValue(String columnName, Object oldValue) throws SQLException {
        switch (columnName) {
            case "Инв №", "Тип", "Модель" -> {
                String input = JOptionPane.showInputDialog(frame, "Новое значение:", oldValue);
                return (input != null && !input.trim().isEmpty()) ? input.trim() : null;
            }
            case "Статус" -> {
                String[] statuses = {"active", "in_repair", "decommissioned", "inactive", "lost", "reserved"};
                return JOptionPane.showInputDialog(frame, "Выберите статус:", "Статус", JOptionPane.QUESTION_MESSAGE, null, statuses, oldValue);
            }
            case "Место" -> {
                List<LocationItem> locs = loadLocations();
                locs.add(0, new LocationItem(-1, "— не указано —"));
                return JOptionPane.showInputDialog(frame, "Местоположение:", "Место", JOptionPane.QUESTION_MESSAGE, null, locs.toArray(), locs.get(0));
            }
            case "Ответственный" -> {
                List<EmployeeItem> emps = loadEmployees();
                emps.add(0, new EmployeeItem(-1, "— не указано —"));
                return JOptionPane.showInputDialog(frame, "Ответственный:", "Сотрудник", JOptionPane.QUESTION_MESSAGE, null, emps.toArray(), emps.get(0));
            }
            default -> { return null; }
        }
    }

    private void loadAssets(String status) {
        assetsTableModel.setRowCount(0);
        String sql = "SELECT a.asset_id, a.inventory_number, a.asset_type, a.model, a.status, " +
                     "ISNULL(l.building_number, '') + '-' + ISNULL(l.room_number, '') AS location, " +
                     "ISNULL(e.last_name + ' ' + e.first_name, '—') AS responsible " +
                     "FROM Assets a " +
                     "LEFT JOIN Locations l ON a.current_location_id = l.location_id " +
                     "LEFT JOIN Employees e ON a.responsible_employee_id = e.employee_id " +
                     (status.equals("Все") ? "" : "WHERE a.status = ? ") +
                     "ORDER BY a.asset_id DESC";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!status.equals("Все")) stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assetsTableModel.addRow(new Object[]{
                        rs.getInt("asset_id"), rs.getString("inventory_number"),
                        rs.getString("asset_type"), rs.getString("model"),
                        rs.getString("status"), rs.getString("location"),
                        rs.getString("responsible"), new JButton("🗑")
                    });
                }
            }
        } catch (SQLException e) { showError("Ошибка загрузки", e); }
    }

    private void showAssetDetails() {
        int row = assetsTable.getSelectedRow();
        if (row == -1) return;
        int assetId = (int) assetsTableModel.getValueAt(row, 0);

        String sql = "SELECT a.*, ISNULL(l.faculty_name, '') + ' / ' + ISNULL(l.department, '') + ' / ' + " +
                     "ISNULL(l.building_number, '') + '-' + ISNULL(l.room_number, '') AS location, " +
                     "ISNULL(e.last_name + ' ' + e.first_name + ' (' + e.position + ')', '—') AS responsible " +
                     "FROM Assets a LEFT JOIN Locations l ON a.current_location_id = l.location_id " +
                     "LEFT JOIN Employees e ON a.responsible_employee_id = e.employee_id WHERE a.asset_id = ?";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, assetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("🔢 Инв. №: ").append(rs.getString("inventory_number")).append("\n")
                      .append("🖥 Тип: ").append(rs.getString("asset_type")).append("\n")
                      .append("🏭 Производитель: ").append(rs.getString("manufacturer")).append("\n")
                      .append("📦 Модель: ").append(rs.getString("model")).append("\n")
                      .append("🔖 Серийный №: ").append(rs.getString("serial_number")).append("\n")
                      .append("📅 Покупка: ").append(rs.getDate("purchase_date")).append("\n")
                      .append("🛡 Гарантия до: ").append(rs.getDate("warranty_end_date")).append("\n")
                      .append("📌 Статус: ").append(rs.getString("status")).append("\n")
                      .append("🌐 IP/MAC: ").append(rs.getString("ip_address")).append(" / ").append(rs.getString("mac_address")).append("\n")
                      .append("📍 Место: ").append(rs.getString("location")).append("\n")
                      .append("👤 Ответственный: ").append(rs.getString("responsible")).append("\n")
                      .append("📋 Спецификация: ").append(rs.getString("specifications")).append("\n")
                      .append("📝 Примечания: ").append(rs.getString("notes"));
                    detailsArea.setText(sb.toString());
                }
            }
        } catch (SQLException e) { showError("Ошибка деталей", e); }
    }

    private void showAddAssetDialog() {
        JDialog dialog = new JDialog(frame, "➕ Новый актив", true);
        dialog.setSize(800, 500);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(0, 2, 5, 8));
        form.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JTextField invField = new JTextField();
        JTextField typeField = new JTextField("laptop");
        JTextField modelField = new JTextField();
        JTextField manufField = new JTextField("Dell");
        JTextField serialField = new JTextField();
        
        JComboBox<LocationItem> locationCombo = new JComboBox<>();
        locationCombo.addItem(new LocationItem(-1, "— не указано —"));
        loadLocations().forEach(locationCombo::addItem);

        JComboBox<EmployeeItem> employeeCombo = new JComboBox<>();
        employeeCombo.addItem(new EmployeeItem(-1, "— не указано —"));
        loadEmployees().forEach(employeeCombo::addItem);

        JComboBox<String> statusCombo = new JComboBox<>(new String[]{"active", "in_repair", "reserved"});
        JFormattedTextField dateField = new JFormattedTextField(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dateField.setValue(LocalDate.now());

        form.add(new JLabel("Инв. номер*")); form.add(invField);
        form.add(new JLabel("Тип*")); form.add(typeField);
        form.add(new JLabel("Модель*")); form.add(modelField);
        form.add(new JLabel("Производитель")); form.add(manufField);
        form.add(new JLabel("Серийный №")); form.add(serialField);
        form.add(new JLabel("Дата покупки*")); form.add(dateField);
        form.add(new JLabel("Статус")); form.add(statusCombo);
        form.add(new JLabel("Место")); form.add(locationCombo);
        form.add(new JLabel("Ответственный")); form.add(employeeCombo);

        JButton saveBtn = new JButton("💾 Сохранить");
        saveBtn.addActionListener(e -> {
            try {
                if (invField.getText().trim().isEmpty()) throw new Exception("Заполните инв. номер");
                
                String sql = "INSERT INTO Assets (inventory_number, asset_type, model, manufacturer, serial_number, purchase_date, status, current_location_id, responsible_employee_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, invField.getText().trim());
                    stmt.setString(2, typeField.getText().trim());
                    stmt.setString(3, modelField.getText().trim());
                    stmt.setString(4, manufField.getText().trim());
                    stmt.setString(5, serialField.getText().trim());
                    stmt.setDate(6, Date.valueOf(LocalDate.parse(dateField.getText(), DateTimeFormatter.ISO_LOCAL_DATE)));
                    stmt.setString(7, (String) statusCombo.getSelectedItem());
                    stmt.setObject(8, ((LocationItem) locationCombo.getSelectedItem()).id == -1 ? null : ((LocationItem) locationCombo.getSelectedItem()).id);
                    stmt.setObject(9, ((EmployeeItem) employeeCombo.getSelectedItem()).id == -1 ? null : ((EmployeeItem) employeeCombo.getSelectedItem()).id);
                    
                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(dialog, "✅ Добавлено!");
                    dialog.dispose();
                    loadAssets("Все");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Ошибка: " + ex.getMessage());
            }
        });

        dialog.add(new JScrollPane(form), BorderLayout.CENTER);
        dialog.add(saveBtn, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private List<LocationItem> loadLocations() {
        List<LocationItem> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT location_id, building_number, room_number, faculty_name FROM Locations ORDER BY faculty_name")) {
            while (rs.next()) {
                list.add(new LocationItem(rs.getInt("location_id"), 
                    String.format("%s (%s-%s)", rs.getString("faculty_name"), rs.getString("building_number"), rs.getString("room_number"))));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private List<EmployeeItem> loadEmployees() {
        List<EmployeeItem> list = new ArrayList<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT employee_id, last_name, first_name, position FROM Employees ORDER BY last_name")) {
            while (rs.next()) {
                list.add(new EmployeeItem(rs.getInt("employee_id"), 
                    String.format("%s %s (%s)", rs.getString("last_name"), rs.getString("first_name"), rs.getString("position"))));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private void showReplacementReport() {
        JDialog dialog = new JDialog(frame, "📊 Отчёт по замене", true);
        dialog.setSize(900, 500);
        
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Инв №", "Тип", "Модель", "Возраст", "Факультет", "Рекомендация"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        
        try (Connection conn = getConnection(); CallableStatement stmt = conn.prepareCall("{CALL Generate_Replacement_Report}");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("asset_id"), rs.getString("inventory_number"), rs.getString("asset_type"),
                    rs.getString("model"), rs.getInt("asset_age_years"), rs.getString("faculty_name"),
                    rs.getString("replacement_status")
                });
            }
        } catch (SQLException e) { showError("Отчёт", e); }

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showError(String title, SQLException ex) {
        JOptionPane.showMessageDialog(frame, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
    }
}
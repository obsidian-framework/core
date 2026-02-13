package fr.kainovaii.obsidian.core.database;

import org.javalite.activejdbc.Base;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public abstract class Migration
{
    protected String type;
    protected Logger logger;

    public abstract void up();

    public abstract void down();

    protected void createTable(String tableName, TableBuilder builder)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");

        List<String> columns = new ArrayList<>();
        builder.build(new Blueprint(columns, type));

        sql.append(String.join(", ", columns));
        sql.append(")");

        Base.exec(sql.toString());
        logger.info("Table created: " + tableName);
    }

    protected void dropTable(String tableName) {
        Base.exec("DROP TABLE IF EXISTS " + tableName);
        logger.info("Table dropped: " + tableName);
    }

    protected void addColumn(String tableName, String columnName, String definition) {
        Base.exec(String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, definition));
        logger.info("Column added: " + tableName + "." + columnName);
    }

    protected void dropColumn(String tableName, String columnName) {
        if (type.equals("sqlite")) {
            logger.warning("SQLite does not support DROP COLUMN - migration skipped");
            return;
        }
        Base.exec(String.format("ALTER TABLE %s DROP COLUMN %s", tableName, columnName));
        logger.info("Column dropped: " + tableName + "." + columnName);
    }

    protected boolean tableExists(String tableName)
    {
        String checkSQL = switch (type) {
            case "mysql" -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            case "postgresql" -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
            default -> "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
        };

        Object result = Base.firstCell(checkSQL, tableName);
        if (result == null) return false;

        long count = result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        return count > 0;
    }


    @FunctionalInterface
    public interface TableBuilder {
        void build(Blueprint blueprint);
    }

    public static class Blueprint {
        private final List<String> columns;
        private final String dbType;

        public Blueprint(List<String> columns, String dbType) {
            this.columns = columns;
            this.dbType = dbType;
        }

        public Blueprint id() {
            return id("id");
        }

        public Blueprint id(String name) {
            String column = switch (dbType) {
                case "mysql" -> name + " INT AUTO_INCREMENT PRIMARY KEY";
                case "postgresql" -> name + " SERIAL PRIMARY KEY";
                default -> name + " INTEGER PRIMARY KEY AUTOINCREMENT";
            };
            columns.add(column);
            return this;
        }

        public Blueprint string(String name) {
            return string(name, 255);
        }

        public Blueprint string(String name, int length) {
            String type = dbType.equals("sqlite") ? "TEXT" : "VARCHAR(" + length + ")";
            columns.add(name + " " + type);
            return this;
        }

        public Blueprint text(String name) {
            columns.add(name + " TEXT");
            return this;
        }

        public Blueprint integer(String name) {
            String type = dbType.equals("postgresql") ? "INTEGER" : "INT";
            columns.add(name + " " + type);
            return this;
        }

        public Blueprint bigInteger(String name) {
            columns.add(name + " BIGINT");
            return this;
        }

        public Blueprint decimal(String name, int precision, int scale) {
            columns.add(name + " DECIMAL(" + precision + "," + scale + ")");
            return this;
        }

        public Blueprint bool(String name) {
            String type = dbType.equals("sqlite") ? "INTEGER" : "BOOLEAN";
            columns.add(name + " " + type);
            return this;
        }

        public Blueprint date(String name) {
            columns.add(name + " DATE");
            return this;
        }

        public Blueprint dateTime(String name) {
            String type = switch (dbType) {
                case "postgresql" -> "TIMESTAMP";
                case "mysql" -> "DATETIME";
                default -> "TEXT"; // SQLite
            };
            columns.add(name + " " + type);
            return this;
        }

        public Blueprint timestamp(String name) {
            columns.add(name + " TIMESTAMP");
            return this;
        }

        public Blueprint timestamps()
        {
            if (dbType.equals("mysql")) {
                columns.add("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            } else if (dbType.equals("postgresql")) {
                columns.add("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } else {
                columns.add("created_at TEXT DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TEXT DEFAULT CURRENT_TIMESTAMP");
            }
            return this;
        }


        public Blueprint notNull() {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " NOT NULL");
            }
            return this;
        }

        public Blueprint unique() {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " UNIQUE");
            }
            return this;
        }

        public Blueprint defaultValue(String value) {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " DEFAULT " + value);
            }
            return this;
        }

        public Blueprint nullable() {
            return this;
        }
    }
}
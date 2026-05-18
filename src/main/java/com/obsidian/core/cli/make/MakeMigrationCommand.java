package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:migration", description = "Generate a new migration class", aliases = {"mmig"})
public class MakeMigrationCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Migration name in snake_case (e.g. create_users_table)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Option(name = "--create", description = "Generate a createTable stub for this table", defaultValue = "")
    private String createTable;

    @Option(name = "--table", description = "Generate an alterTable stub for this table", defaultValue = "")
    private String alterTable;

    @Override
    public void run()
    {
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".database.migrations"
                : pkg;
        runInternal(name, resolvedPkg, createTable, !createTable.isBlank());
    }

    /** Called programmatically from MakeModelCommand. */
    void runInternal(String migrationName, String resolvedPkg, String table, boolean isCreate)
    {
        String className = MakeFileWriter.toPascalCase(migrationName);
        String fileName  = MakeFileWriter.migrationTimestamp() + "_" + className;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(fileName + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className, table, isCreate)))
            MakeFileWriter.printCreated("Migration", target);
    }

    private String stub(String pkg, String className, String table, boolean isCreate)
    {
        boolean isAlter = !alterTable.isBlank() && !isCreate;
        String  t       = isCreate ? table : (isAlter ? alterTable : "table_name");

        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.database.Migration;\n"
             + "import com.obsidian.core.database.Blueprint;\n\n"
             + "public class " + className + " extends Migration\n{\n"
             + "    @Override\n"
             + "    public void up()\n    {\n"
             + (isCreate
                 ? "        createTable(\"" + t + "\", t -> {\n"
                 + "            t.id();\n"
                 + "            t.timestamps();\n"
                 + "        });\n"
                 : isAlter
                 ? "        alterTable(\"" + t + "\", t -> {\n"
                 + "            // t.string(\"column_name\");\n"
                 + "        });\n"
                 : "        // TODO\n")
             + "    }\n\n"
             + "    @Override\n"
             + "    public void down()\n    {\n"
             + (isCreate ? "        dropTable(\"" + t + "\");\n" : "        // TODO\n")
             + "    }\n"
             + "}\n";
    }
}

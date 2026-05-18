package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:model", description = "Generate a new model class", aliases = {"mm"})
public class MakeModelCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Model class name (e.g. User, BlogPost)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Option(name = "--table", description = "Override the database table name", defaultValue = "")
    private String table;

    @Option(name = "--migration", description = "Also generate the companion migration", flag = true)
    private boolean migration;

    @Option(name = "--repository", description = "Also generate the companion repository", flag = true)
    private boolean repository;

    @Option(name = "--soft-deletes", description = "Enable soft deletes", flag = true)
    private boolean softDeletes;

    @Override
    public void run()
    {
        String className = MakeFileWriter.normalize(name, "");
        String basePkg   = pkg.isBlank() ? MakeFileWriter.detectBasePackage() : pkg;
        String modelPkg  = basePkg + ".models";
        String tableName = table.isBlank() ? MakeFileWriter.toTableName(className) : table;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(modelPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(modelPkg, className, tableName)))
            MakeFileWriter.printCreated("Model", target);

        if (migration) {
            MakeMigrationCommand cmd = new MakeMigrationCommand();
            cmd.runInternal("create_" + tableName + "_table", basePkg + ".database.migrations", tableName, true);
        }

        if (repository) {
            MakeRepositoryCommand cmd = new MakeRepositoryCommand();
            cmd.runInternal(className, modelPkg, basePkg + ".repositories");
        }
    }

    private String stub(String pkg, String className, String tableName)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import com.obsidian.core.database.orm.model.Model;\n");
        sb.append("import com.obsidian.core.database.orm.model.Table;\n\n");
        sb.append("import java.util.List;\n\n");
        sb.append("@Table(\"").append(tableName).append("\")\n");
        sb.append("public class ").append(className).append(" extends Model\n{\n");
        sb.append("    @Override\n");
        sb.append("    protected List<String> fillable()\n");
        sb.append("    {\n");
        sb.append("        return List.of(/* \"column1\", \"column2\" */);\n");
        sb.append("    }\n");
        if (softDeletes) {
            sb.append("\n    @Override\n");
            sb.append("    protected boolean softDeletes() { return true; }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}

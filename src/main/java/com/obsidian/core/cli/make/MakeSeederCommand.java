package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:seeder", description = "Generate a new database seeder class", aliases = {"ms"})
public class MakeSeederCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Seeder class name (e.g. UserSeeder)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Override
    public void run()
    {
        String className   = MakeFileWriter.normalize(name, "Seeder");
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".database.seeders"
                : pkg;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className)))
            MakeFileWriter.printCreated("Seeder", target);
    }

    private String stub(String pkg, String className)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.database.seeder.SeederInterface;\n"
             + "import com.obsidian.core.database.seeder.annotations.Seeder;\n\n"
             + "@Seeder\n"
             + "public class " + className + " implements SeederInterface\n{\n"
             + "    @Override\n"
             + "    public void seed()\n"
             + "    {\n"
             + "        // TODO: insert seed data\n"
             + "    }\n"
             + "}\n";
    }
}

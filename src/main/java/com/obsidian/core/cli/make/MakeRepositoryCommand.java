package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:repository", description = "Generate a new repository class", aliases = {"mr"})
public class MakeRepositoryCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Repository class name (e.g. UserRepository)")
    private String name;

    @Option(name = "--model", description = "Model class this repository wraps (e.g. User)", defaultValue = "")
    private String model;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Override
    public void run()
    {
        String className   = MakeFileWriter.normalize(name, "Repository");
        String modelName   = model.isBlank() ? className.replace("Repository", "") : model;
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".repositories"
                : pkg;

        write(className, modelName, null, resolvedPkg);
    }

    /** Called programmatically from MakeModelCommand. */
    void runInternal(String modelClassName, String modelPkg, String repoPkg)
    {
        String className = modelClassName + "Repository";
        String fqModel   = modelPkg + "." + modelClassName;
        write(className, modelClassName, fqModel, repoPkg);
    }

    private void write(String className, String modelName, String modelFqn, String resolvedPkg)
    {
        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className, modelName, modelFqn)))
            MakeFileWriter.printCreated("Repository", target);
    }

    private String stub(String pkg, String className, String modelName, String modelFqn)
    {
        String importLine = modelFqn != null
                ? "import " + modelFqn + ";\n"
                : "// import your model here\n";

        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.database.orm.repository.BaseRepository;\n"
             + "import com.obsidian.core.di.annotations.Repository;\n"
             + importLine + "\n"
             + "@Repository\n"
             + "public class " + className + " extends BaseRepository<" + modelName + ">\n{\n"
             + "    public " + className + "()\n"
             + "    {\n"
             + "        super(" + modelName + ".class);\n"
             + "    }\n\n"
             + "    // Add custom query methods here\n"
             + "}\n";
    }
}

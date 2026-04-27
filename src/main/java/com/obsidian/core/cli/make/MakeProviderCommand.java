package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:provider", description = "Generate a new service provider class", aliases = {"mp"})
public class MakeProviderCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Provider class name (e.g. MailServiceProvider)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Override
    public void run()
    {
        String className   = MakeFileWriter.normalize(name, "ServiceProvider");
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".providers"
                : pkg;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className)))
            MakeFileWriter.printCreated("ServiceProvider", target);
    }

    private String stub(String pkg, String className)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.di.ServiceProvider;\n\n"
             + "public class " + className + " extends ServiceProvider\n{\n"
             + "    @Override\n"
             + "    public void register()\n"
             + "    {\n"
             + "        // singleton(MyService.class, new MyServiceImpl());\n"
             + "        // bind(MyInterface.class, MyImpl.class);\n"
             + "    }\n"
             + "}\n";
    }
}

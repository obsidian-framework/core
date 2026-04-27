package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:middleware", description = "Generate a new middleware class", aliases = {"mmw"})
public class MakeMiddlewareCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Middleware class name (e.g. AuthMiddleware)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Override
    public void run()
    {
        String className   = MakeFileWriter.normalize(name, "Middleware");
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".middleware"
                : pkg;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className)))
            MakeFileWriter.printCreated("Middleware", target);
    }

    private String stub(String pkg, String className)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.http.middleware.Middleware;\n"
             + "import spark.Request;\n"
             + "import spark.Response;\n\n"
             + "public class " + className + " implements Middleware\n{\n"
             + "    @Override\n"
             + "    public void handle(Request req, Response res) throws Exception\n"
             + "    {\n"
             + "        // TODO: implement middleware logic\n"
             + "    }\n"
             + "}\n";
    }
}

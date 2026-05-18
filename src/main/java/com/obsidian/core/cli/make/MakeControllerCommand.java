package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:controller", description = "Generate a new controller class", aliases = {"mc"})
public class MakeControllerCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Controller class name (e.g. UserController)")
    private String name;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Option(name = "--resource", description = "Generate index/show/create/store/edit/update/destroy stubs", flag = true)
    private boolean resource;

    @Override
    public void run()
    {
        String className = MakeFileWriter.normalize(name, "Controller");
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".controllers"
                : pkg;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        String content = resource ? resourceStub(resolvedPkg, className) : basicStub(resolvedPkg, className);

        if (MakeFileWriter.write(target, content))
            MakeFileWriter.printCreated("Controller", target);
    }

    private String basicStub(String pkg, String name)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.http.controller.Controller;\n"
             + "import com.obsidian.core.routing.annotations.Controller as Ctrl;\n"
             + "import com.obsidian.core.routing.methods.GET;\n"
             + "import spark.Request;\n"
             + "import spark.Response;\n\n"
             + "@Ctrl\n"
             + "public class " + name + " extends Controller\n{\n"
             + "    @GET(\"/\")\n"
             + "    public Object index(Request req, Response res)\n"
             + "    {\n"
             + "        return view(\"index\");\n"
             + "    }\n"
             + "}\n";
    }

    private String resourceStub(String pkg, String name)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.http.controller.Controller;\n"
             + "import com.obsidian.core.routing.annotations.Controller as Ctrl;\n"
             + "import com.obsidian.core.routing.methods.*;\n"
             + "import spark.Request;\n"
             + "import spark.Response;\n\n"
             + "@Ctrl\n"
             + "public class " + name + " extends Controller\n{\n"
             + "    @GET(\"/\")\n"
             + "    public Object index(Request req, Response res) { return view(\"index\"); }\n\n"
             + "    @GET(\"/create\")\n"
             + "    public Object create(Request req, Response res) { return view(\"create\"); }\n\n"
             + "    @POST(\"/\")\n"
             + "    public Object store(Request req, Response res) { return redirect(\"/\"); }\n\n"
             + "    @GET(\"/:id\")\n"
             + "    public Object show(Request req, Response res) { return view(\"show\"); }\n\n"
             + "    @GET(\"/:id/edit\")\n"
             + "    public Object edit(Request req, Response res) { return view(\"edit\"); }\n\n"
             + "    @PUT(\"/:id\")\n"
             + "    public Object update(Request req, Response res) { return redirect(\"/\"); }\n\n"
             + "    @DELETE(\"/:id\")\n"
             + "    public Object destroy(Request req, Response res) { return redirect(\"/\"); }\n"
             + "}\n";
    }
}

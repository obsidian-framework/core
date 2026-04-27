package com.obsidian.core.cli.make;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.nio.file.Path;

@Command(name = "make:command", description = "Generate a new CLI command class", aliases = {"mcc"})
public class MakeCommandCommand implements Runnable
{
    @Param(index = 0, name = "name", description = "Command class name (e.g. SendEmailsCommand)")
    private String name;

    @Option(name = "--cmd-name", description = "CLI command name (e.g. send:emails, default: inferred)", defaultValue = "")
    private String cmdName;

    @Option(name = "--package", description = "Target package (overrides auto-detection)", defaultValue = "")
    private String pkg;

    @Override
    public void run()
    {
        String className   = MakeFileWriter.normalize(name, "Command");
        String cliName     = cmdName.isBlank() ? inferCliName(className) : cmdName;
        String resolvedPkg = pkg.isBlank()
                ? MakeFileWriter.detectBasePackage() + ".cli.commands"
                : pkg;

        Path target = MakeFileWriter.sourceRoot()
                .resolve(MakeFileWriter.pkgToPath(resolvedPkg))
                .resolve(className + ".java");

        if (MakeFileWriter.write(target, stub(resolvedPkg, className, cliName)))
            MakeFileWriter.printCreated("Command", target);
    }

    private String stub(String pkg, String className, String cliName)
    {
        return "package " + pkg + ";\n\n"
             + "import com.obsidian.core.cli.Printer;\n"
             + "import com.obsidian.core.cli.annotations.Command;\n"
             + "import com.obsidian.core.cli.annotations.Option;\n"
             + "import com.obsidian.core.cli.annotations.Param;\n\n"
             + "@Command(name = \"" + cliName + "\", description = \"TODO\")\n"
             + "public class " + className + " implements Runnable\n{\n"
             + "    // @Param(index = 0, name = \"arg\", description = \"...\")\n"
             + "    // private String arg;\n\n"
             + "    // @Option(name = \"--flag\", description = \"...\", flag = true)\n"
             + "    // private boolean flag;\n\n"
             + "    @Override\n"
             + "    public void run()\n"
             + "    {\n"
             + "        // TODO\n"
             + "        Printer.ok(\"Done.\");\n"
             + "    }\n"
             + "}\n";
    }

    /** "SendEmailsCommand" → "send:emails" */
    private String inferCliName(String className)
    {
        String stripped = className.endsWith("Command")
                ? className.substring(0, className.length() - 7)
                : className;
        return stripped.replaceAll("([a-z])([A-Z])", "$1:$2").toLowerCase();
    }
}

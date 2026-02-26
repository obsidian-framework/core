package fr.kainovaii.obsidian.template.extension;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.lexer.Token;
import io.pebbletemplates.pebble.lexer.TokenStream;
import io.pebbletemplates.pebble.node.AbstractRenderableNode;
import io.pebbletemplates.pebble.node.RenderableNode;
import io.pebbletemplates.pebble.parser.Parser;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import io.pebbletemplates.pebble.tokenParser.TokenParser;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pebble extension that registers a custom {@code markdown} tag.
 *
 * This tag reads a Markdown file from the given path, converts it to HTML
 * using {@link MarkdownFilter}, and writes the result directly to the template output.
 *
 * Usage in a Pebble template:
 * <pre>
 * {% markdown "./fichiers/installation.md" %}
 * </pre>
 */
public class MarkdownTag extends AbstractExtension
{
    /**
     * Returns the list of token parsers registered by this extension.
     *
     * @return a list containing the {@code markdown} token parser
     */
    @Override
    public List<TokenParser> getTokenParsers()
    {
        return List.of(new TokenParser()
        {
            /**
             * Returns the tag name handled by this parser.
             *
             * @return {@code "markdown"}
             */
            @Override
            public String getTag() { return "markdown"; }

            /**
             * Parses the {@code markdown} tag from the token stream.
             *
             * @param token  the opening token of the tag
             * @param parser the Pebble parser
             * @return a {@link MarkdownNode} configured with the resolved file path
             */
            @Override
            public RenderableNode parse(Token token, Parser parser)
            {
                TokenStream stream = parser.getStream();
                stream.next();

                Token pathToken = stream.current();
                String path = pathToken.getValue()
                        .replaceAll("^\"|\"$", "")
                        .replaceAll("^'|'$", "");
                stream.next();

                stream.expect(Token.Type.EXECUTE_END);

                return new MarkdownNode(token.getLineNumber(), path);
            }
        });
    }

    /**
     * Renderable node that reads a Markdown file and writes its HTML output to the template writer.
     */
    public static class MarkdownNode extends AbstractRenderableNode
    {
        /** Relative path to the Markdown file as written in the template. */
        private final String path;

        /**
         * Constructs a new MarkdownNode.
         *
         * @param lineNumber the line number in the template where the tag appears
         * @param path       the relative path to the Markdown source file
         */
        public MarkdownNode(int lineNumber, String path) {
            super(lineNumber);
            this.path = path;
        }

        /**
         * Reads the Markdown file, converts it to HTML, and writes it to the output writer.
         *
         * @param self    the current Pebble template instance
         * @param writer  the output writer to write rendered HTML to
         * @param context the current evaluation context
         * @throws IOException if the file cannot be read
         */
        @Override
        public void render(PebbleTemplateImpl self, Writer writer, EvaluationContextImpl context)
                throws IOException
        {
            String base = System.getProperty("user.dir") + "/src/main/resources/";
            Path templatePath = Path.of(base + self.getName()).getParent();
            Path resolved = templatePath.resolve(path).normalize();

            String markdown = Files.readString(resolved);
            String html = MarkdownFilter.render(markdown);
            writer.write(html);
        }

        /**
         * Accepts a {@link NodeVisitor} for AST traversal.
         *
         * @param visitor the visitor to accept
         */
        @Override
        public void accept(NodeVisitor visitor) {
            visitor.visit(this);
        }
    }
}
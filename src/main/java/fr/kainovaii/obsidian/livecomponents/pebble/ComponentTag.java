package fr.kainovaii.obsidian.livecomponents.pebble;

import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.livecomponents.http.RequestContext;
import fr.kainovaii.obsidian.livecomponents.session.SessionContext;
import io.pebbletemplates.pebble.error.ParserException;
import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.lexer.Token;
import io.pebbletemplates.pebble.lexer.TokenStream;
import io.pebbletemplates.pebble.node.AbstractRenderableNode;
import io.pebbletemplates.pebble.node.RenderableNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.parser.Parser;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import io.pebbletemplates.pebble.tokenParser.TokenParser;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * TokenParser for the {@code {% component %}} tag.
 */
public class ComponentTag implements TokenParser
{
    @Override
    public String getTag() {
        return "component";
    }

    @Override
    public RenderableNode parse(Token token, Parser parser) throws ParserException
    {
        TokenStream stream = parser.getStream();
        int lineNumber = token.getLineNumber();

        // consume the "component" tag token
        stream.next();

        // parse the component name expression (string literal or variable)
        Expression<?> nameExpr = parser.getExpressionParser().parseExpression();

        // optionally parse "with { ... }"
        Expression<?> propsExpr = null;
        if (stream.current().getType() == Token.Type.NAME
                && "with".equals(stream.current().getValue())) {
            stream.next(); // consume "with"
            propsExpr = parser.getExpressionParser().parseExpression();
        }

        stream.expect(Token.Type.EXECUTE_END);

        return new ComponentNode(lineNumber, nameExpr, propsExpr);
    }

    // -------------------------------------------------------------------------

    static class ComponentNode extends AbstractRenderableNode
    {
        private final Expression<?> nameExpr;
        private final Expression<?> propsExpr;

        ComponentNode(int lineNumber, Expression<?> nameExpr, Expression<?> propsExpr) {
            super(lineNumber);
            this.nameExpr = nameExpr;
            this.propsExpr = propsExpr;
        }

        @Override
        public void render(PebbleTemplateImpl self, Writer writer, EvaluationContextImpl context)
                throws IOException
        {
            String componentName = String.valueOf(nameExpr.evaluate(self, context));

            Map<String, Object> props = null;
            if (propsExpr != null) {
                Object evaluated = propsExpr.evaluate(self, context);
                if (evaluated instanceof Map) {
                    //noinspection unchecked
                    props = (Map<String, Object>) evaluated;
                }
            }

            spark.Session session = SessionContext.get();
            spark.Request request  = RequestContext.get();

            String html = Obsidian.getComponentManager().mount(componentName, session, request, props);
            writer.write(html != null ? html : "");
        }

        @Override
        public void accept(NodeVisitor visitor) {
            visitor.visit(this);
        }
    }
}
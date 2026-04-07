package com.nicleo.fastsql.compiler;

import com.nicleo.fastsql.core.TemplateSupport;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;

public final class FastSqlCompilerPlugin implements Plugin {
    @Override
    public String getName() {
        return "FastSqlCompiler";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context context = ((BasicJavacTask) task).getContext();
        ParserFactory parserFactory = ParserFactory.instance(context);
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit) e.getCompilationUnit();
                compilationUnit.accept(new FastSqlTranslator(parserFactory));
            }
        });
    }

    private static final class FastSqlTranslator extends TreeTranslator {
        private final ParserFactory parserFactory;

        private FastSqlTranslator(ParserFactory parserFactory) {
            this.parserFactory = parserFactory;
        }

        @Override
        public void visitApply(JCTree.JCMethodInvocation tree) {
            super.visitApply(tree);
            if (!isFastSqlCall(tree) || tree.args.size() != 1 || !(tree.args.head instanceof JCTree.JCLiteral literal)) {
                result = tree;
                return;
            }
            result = replaceLiteralIfTemplate(tree, literal);
        }

        @Override
        public void visitLiteral(JCTree.JCLiteral tree) {
            super.visitLiteral(tree);
            result = replaceLiteralIfTemplate(tree, tree);
        }

        private boolean isFastSqlCall(JCTree.JCMethodInvocation tree) {
            if (tree.meth instanceof JCTree.JCFieldAccess fieldAccess) {
                return fieldAccess.name.contentEquals("sql") && fieldAccess.selected.toString().endsWith("FastSql");
            }
            if (tree.meth instanceof JCTree.JCIdent ident) {
                return ident.name.contentEquals("sql");
            }
            return false;
        }

        private JCTree replaceLiteralIfTemplate(JCTree originalTree, JCTree.JCLiteral literal) {
            if (!(literal.getValue() instanceof String template) || !TemplateSupport.hasTemplateSyntax(template)) {
                return originalTree;
            }
            try {
                return parserFactory.newParser(
                        TemplateLowering.toSupplierInvocation(template),
                        false,
                        false,
                        false
                ).parseExpression();
            } catch (RuntimeException ex) {
                return originalTree;
            }
        }
    }
}

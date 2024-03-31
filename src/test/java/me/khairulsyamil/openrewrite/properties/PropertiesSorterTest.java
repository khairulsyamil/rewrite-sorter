package me.khairulsyamil.openrewrite.properties;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

class PropertiesSorterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PropertiesSorter(null));
    }

    @Test
    public void noChanges() {
        rewriteRun(
                properties(
                        """
                                label.abc=ABC
                                label.def=DEF
                                """)
        );
    }

    @Test
    public void simpleChange() {
        rewriteRun(
                properties(
                        """
                                label.def=DEF
                                label.abc=ABC
                                """,
                        """
                                label.abc=ABC
                                label.def=DEF
                                """)
        );
    }

    @Test
    public void emptyLineRemoval() {
        rewriteRun(
                properties(
                        """
                                label.abc=ABC
                                
                                label.def=DEF
                                """,
                        """
                                label.abc=ABC
                                label.def=DEF
                                """)
        );
    }

    @Test
    public void commentRemoval() {
        rewriteRun(
                properties(
                        """
                                # Group DEF
                                label.def.2=DEF 2
                                label.def.1=DEF 1
                                
                                
                                
                                # Group ABC
                                label.abc.2=ABC 2
                                label.abc.1=ABC 1
                                """,
                        """
                                label.abc.1=ABC 1
                                label.abc.2=ABC 2
                                label.def.1=DEF 1
                                label.def.2=DEF 2
                                """)
        );
    }

    @Test
    public void duplicateRemoval() {
        rewriteRun(
                properties(
                        """
                                label.abc=ABC
                                
                                label.def=DEF
                                label.abc=ABC
                                """,
                        """
                                label.abc=ABC
                                label.def=DEF
                                """)
        );
    }

    @Test
    public void duplicateWarning() {
        rewriteRun(
                properties(
                        """
                                label.abc=ABC
                                
                                label.def=DEF
                                label.abc=ABCG
                                """,
                        """
                                label.abc=ABC
                                label.abc=ABCG
                                label.def=DEF
                                """)
        );
    }
}
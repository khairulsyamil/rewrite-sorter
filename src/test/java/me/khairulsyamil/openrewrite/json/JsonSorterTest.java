package me.khairulsyamil.openrewrite.json;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.json.Assertions.json;

class JsonSorterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JsonSorter(null));
    }

    public <T> int methodA(List<T> list) {
        return list.size();
    }

    @Test
    public void noChanges() {
        rewriteRun(
                json(
                        """
                                {
                                    "label.abc": "ABC",
                                    "label.def": "DEF"
                                }
                                """)
        );
    }

    @Test
    public void simpleChange() {
        rewriteRun(
                json(
                        """
                                {
                                    "label.def": "DEF",
                                    "label.abc": "ABC"
                                }
                                """, """
                                {
                                    "label.abc": "ABC",
                                    "label.def": "DEF"
                                }
                                """)
        );
    }

    @Test
    public void nestedChange() {
        rewriteRun(
                json("""
                        {
                            "label.def": "DEF",
                            "label.nest": {
                                "nested.2": "NESTED 2",
                                "nested.1": "NESTED 1"
                            },
                            "label.abc": "ABC"
                        }
                        """, """
                        {
                            "label.abc": "ABC",
                            "label.def": "DEF",
                            "label.nest": {
                                "nested.1": "NESTED 1",
                                "nested.2": "NESTED 2"
                            }
                        }
                        """
                )
        );
    }

    @Test
    public void duplicateRemoval() {
        rewriteRun(
                json(
                        """
                                {
                                    "label.def": "DEF",
                                    "label.abc": "ABC",
                                    "label.def": "DEF"
                                }
                                """, """
                                {
                                    "label.abc": "ABC",
                                    "label.def": "DEF"
                                }
                                """)
        );
    }
}
package me.khairulsyamil.openrewrite.java;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JavaMethodSorterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JavaMethodSorter());
    }

    @Test
    public void noClassChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                class TestClass {
                    private int property;
                
                    /***
                     * Test Javadoc
                     * @param cd
                     * @return
                     */
                    public <T> int methodA(List<T> list) {
                        return list.size();
                    }
                }
                """));
    }

    @Test
    public void simpleClassChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                class TestClass {
                    private int property3;
                    private int property1;
                
                    public <T> int methodB(List<T> list) {
                        return list.size();
                    }
                
                    /***
                     * Test Javadoc
                     * @param cd
                     * @return
                     */
                    public <T> int methodA(List<T> list) {
                        return list.size();
                    }
                }
                """, """
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                class TestClass {
                    private int property1;
                    private int property3;
                
                    /***
                     * Test Javadoc
                     * @param cd
                     * @return
                     */
                    public <T> int methodA(List<T> list) {
                        return list.size();
                    }
                    
                    public <T> int methodB(List<T> list) {
                        return list.size();
                    }
                }
                """));
    }

    @Test
    public void noInterfaceChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                interface TestClass {
                    <T> int methodA(List<T> list);
                }
                """));
    }

    @Test
    public void simpleInterfaceChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                interface TestClass {
                    int methodB();
                    <T> int methodA(List<T> list);
                }
                """, """
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                 
                interface TestClass {
                    <T> int methodA(List<T> list);
                    int methodB();
                }
                """));
    }

    @Test
    public void noEnumChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                enum TestEnum {
                    A (10),
                    B (20),
                    C (30)
                    ;
                    
                    private final int count;
                    
                    private TestEnum(int count)  {
                        this.count = count;
                    }
                
                    public int getCount() {
                        return count;
                    }
                }
                """));
    }

    @Test
    public void noStaticClassChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                                                
                class TestClass {
                    private int property;
                    
                    private TestClass()  {
                    }
                
                    public int getProperty() {
                        return property;
                    }
                    
                    public static class TestInnerClass {
                        private int innerProperty;
                        
                        private TestInnerClass()  {
                        }
                    
                        public int getInnerProperty() {
                            return innerProperty;
                        }
                    }
                }
                """));
    }

    @Test
    public void noAnnotationChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                import java.lang.annotation.Documented;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                
                @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
                @Retention(RetentionPolicy.RUNTIME)
                @Documented
                public @interface BeforeEach {
                }
                
                interface TestInterface {
                    <T> int methodA(List<T> list);
                    <T> int methodB(List<T> list);
                }
                                                
                class TestClass implements TestInterface {
                    private int property;
                
                    @Override
                    @BeforeEach
                    public <T> int methodA(List<T> list) {
                        return list.size();
                    }
                    
                    @Override
                    public <T> int methodB(List<T> list) {
                        return list.size();
                    }
                }
                """));
    }

    @Test
    public void noStaticInitializerChanges() {
        rewriteRun(java("""
                package me.khairulsyamil.test;
                                                
                import java.util.List;
                import java.util.ArrayList;
                                                
                class TestClass {
                    private static final List<Integer> a = new ArrayList<>();
                    static {
                        a.add(1);
                    }
                }
                """));
    }
}
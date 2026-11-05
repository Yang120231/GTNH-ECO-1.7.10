package cn.dancingsnow.neoecoae.platform.gt.upload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassVisitor;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.MethodVisitor;
import org.spongepowered.asm.lib.Opcodes;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import cn.dancingsnow.neoecoae.platform.TestFluids;

public class GtFluidOutputRoutesTest {

    private static final Class<?> VERIFIER = isolatedVerifier();

    @Test
    void fluidOnlyOutputCanMatchExactly() throws Exception {
        assertEquals("EXACT", verify(1000, TestFluids.LAVA, 3456));
    }

    @Test
    void fluidOnlyOutputCanMatchAConsistentMultiple() throws Exception {
        assertEquals("EXACT", verify(2000, TestFluids.LAVA, 6912));
    }

    @Test
    void fluidOnlyOutputStillRequiresTheCorrectQuantity() throws Exception {
        assertEquals("RATIO_MISMATCH", verify(1000, TestFluids.LAVA, 3455));
        assertEquals("RATIO_MISMATCH", verify(2000, TestFluids.LAVA, 3456));
    }

    @Test
    void fluidOnlyOutputStillRequiresTheCorrectFluid() throws Exception {
        assertEquals("NONE", verify(1000, TestFluids.WATER, 3456));
    }

    @Test
    void missingOutputsAreRejectedBeforeRecipeLookup() throws Exception {
        Class<?> mapType = Class.forName("gregtech.api.recipe.RecipeMap");
        Object map = mock(mapType);
        assertEquals("NONE", verify(mapType, map, pattern(1000, new IAEStack<?>[0])));
        verifyNoInteractions(map);
    }

    private static String verify(int inputAmount, Fluid output, int outputAmount) throws Exception {
        Class<?> mapType = Class.forName("gregtech.api.recipe.RecipeMap");
        Class<?> queryType = Class.forName("gregtech.api.recipe.FindRecipeQuery");
        Class<?> recipeType = Class.forName("gregtech.api.util.GTRecipe");
        Object map = mock(mapType), query = mock(queryType, RETURNS_SELF), recipe = mock(recipeType);
        recipeType.getField("mFluidInputs")
            .set(recipe, new FluidStack[] { fluid(TestFluids.WATER, 1000) });
        recipeType.getField("mFluidOutputs")
            .set(recipe, new FluidStack[] { fluid(TestFluids.LAVA, 3456) });
        when(
            mapType.getMethod("findRecipeQuery")
                .invoke(map)).thenReturn(query);
        Predicate<?>[] filter = { null };
        doAnswer(invocation -> {
            filter[0] = invocation.getArgument(0);
            return query;
        }).when(query);
        queryType.getMethod("filter", Predicate.class)
            .invoke(query, any(Predicate.class));
        doAnswer(
            invocation -> Stream.of(recipe)
                .filter(candidate -> accepts(filter[0], candidate))).when(query);
        queryType.getMethod("findAll")
            .invoke(query);
        return verify(mapType, map, pattern(inputAmount, new IAEStack<?>[] { aeFluid(output, outputAmount) }));
    }

    @SuppressWarnings("unchecked")
    private static boolean accepts(Predicate<?> filter, Object recipe) {
        return ((Predicate<Object>) filter).test(recipe);
    }

    private static String verify(Class<?> mapType, Object map, ICraftingPatternDetails details) throws Exception {
        Method verify = VERIFIER.getDeclaredMethod("verify", mapType, ICraftingPatternDetails.class, ItemStack.class);
        verify.setAccessible(true);
        return verify.invoke(null, map, details, null)
            .toString();
    }

    private static ICraftingPatternDetails pattern(int inputAmount, IAEStack<?>[] outputs) {
        IAEStack<?>[] inputs = { aeFluid(TestFluids.WATER, inputAmount) };
        var details = mock(ICraftingPatternDetails.class);
        when(details.getCondensedAEInputs()).thenReturn(inputs);
        when(details.getCondensedAEOutputs()).thenReturn(outputs);
        return details;
    }

    private static IAEFluidStack aeFluid(Fluid fluid, int amount) {
        FluidStack nativeStack = fluid(fluid, amount);
        var stack = mock(IAEFluidStack.class);
        when(stack.getFluidStack()).thenReturn(nativeStack);
        when(stack.getStackSize()).thenReturn((long) amount);
        return stack;
    }

    private static FluidStack fluid(Fluid fluid, int amount) {
        var stack = mock(FluidStack.class);
        when(stack.getFluid()).thenReturn(fluid);
        when(stack.copy()).thenAnswer(invocation -> fluid(fluid, amount));
        stack.amount = amount;
        return stack;
    }

    /** Exercise the actual verifier while isolating GTUtility's unrelated optional-mod and JVM backport linkage. */
    private static Class<?> isolatedVerifier() {
        String verifier = GtRecipeVerifier.class.getName();
        ClassLoader loader = new ClassLoader(GtFluidOutputRoutesTest.class.getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(verifier) && !name.startsWith(verifier + "$")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> found = findLoadedClass(name);
                    if (found == null) {
                        try (var stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5, writer) {

                                @Override
                                public MethodVisitor visitMethod(int access, String method, String descriptor,
                                    String signature, String[] exceptions) {
                                    return new MethodVisitor(
                                        Opcodes.ASM5,
                                        super.visitMethod(access, method, descriptor, signature, exceptions)) {

                                        @Override
                                        public void visitMethodInsn(int opcode, String owner, String method,
                                            String descriptor, boolean isInterface) {
                                            if (owner.equals("gregtech/api/util/GTUtility")
                                                && method.equals("areFluidsEqual"))
                                                owner = NativeFluids.class.getName()
                                                    .replace('.', '/');
                                            super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                                        }
                                    };
                                }
                            }, 0);
                            byte[] bytes = writer.toByteArray();
                            found = defineClass(name, bytes, 0, bytes.length);
                        } catch (java.io.IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(found);
                    return found;
                }
            }
        };
        try {
            return loader.loadClass(verifier);
        } catch (ClassNotFoundException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public static final class NativeFluids {

        public static boolean areFluidsEqual(FluidStack left, FluidStack right, boolean ignoreNbt) {
            return left != null && right != null
                && left.getFluid() == right.getFluid()
                && (ignoreNbt || java.util.Objects.equals(left.tag, right.tag));
        }
    }
}

package com.dreammaster.witchery;

import com.dreammaster.coremod.DreamCoreMod;
import com.emoniph.witchery.brewing.AltarPower;
import com.emoniph.witchery.brewing.BrewItemKey;
import com.emoniph.witchery.brewing.WitcheryBrewRegistry;
import com.emoniph.witchery.brewing.action.BrewAction;
import com.emoniph.witchery.brewing.action.BrewActionRitualRecipe;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.function.Function;
import java.util.stream.Stream;

class WitcheryBrewRegistryAccessor {
    static final Logger log = LogManager.getLogger("WitcheryCompat");
    static final Method methodRegister;
    static final Hashtable<BrewItemKey, BrewAction> ingredient;

    static {
        Hashtable<BrewItemKey, BrewAction> ingredient1;
        Method tmp;
        Field field;
        try {
            final Class<?> clazz = Class.forName("com.emoniph.witchery.brewing.WitcheryBrewRegistry", false, WitcheryPlugin.class.getClassLoader());
            tmp = clazz.getDeclaredMethod("register", BrewAction.class);
            tmp.setAccessible(true);
            field = clazz.getDeclaredField("ingredients");
            field.setAccessible(true);
            ingredient1 = getIngredient(field);
        } catch (NoSuchMethodException | ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            log.error("Cannot find Witchery brew registry stuff. Related functionality will have no effect!", e);
            tmp = null;
            ingredient1 = null;
        }
        ingredient = ingredient1;
        methodRegister = tmp;
    }

    @SuppressWarnings("unchecked")
    private static Hashtable<BrewItemKey, BrewAction> getIngredient(Field field) throws IllegalAccessException {
        return (Hashtable<BrewItemKey, BrewAction>) field.get(WitcheryBrewRegistry.INSTANCE);
    }

    static void registerBrewAction(BrewAction action) {
        if (methodRegister != null) {
            try {
                methodRegister.invoke(null, action);
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error("Error registering brew action", e);
            }
        }
    }

    static void modifyBrewRecipe(BrewActionRitualRecipe ritualRecipe, Function<Stream<BrewActionRitualRecipe.Recipe>, Stream<BrewActionRitualRecipe.Recipe>> modification) {
        removeAction(ritualRecipe);
        AltarPower power = new AltarPower(0);
        ritualRecipe.accumulatePower(power);
        registerBrewAction(
                new BrewActionRitualRecipe(ritualRecipe.ITEM_KEY, power, modification.apply(ritualRecipe.getExpandedRecipes()
                        .stream())
                        .map(r -> new BrewActionRitualRecipe.Recipe(r.result, Arrays.copyOf(r.ingredients, r.ingredients.length - 1)))
                        .toArray(BrewActionRitualRecipe.Recipe[]::new))
        );
    }

    static void removeAction(BrewActionRitualRecipe action) {
        ingredient.remove(action.ITEM_KEY);
        WitcheryBrewRegistry.INSTANCE.getRecipes().remove(action);
    }

    static boolean isCauldronRecipeMatch(BrewActionRitualRecipe.Recipe recipe, ItemStack[] items) {
        final ItemStack[] ingredients = recipe.ingredients;
        final int length = ingredients.length;
        if (length != items.length)
            return false;
        boolean[] found = new boolean[length];
        for (ItemStack item : items) {
            boolean foundThisRound = false;
            for (int i = 0; i < length; i++) {
                if (!found[i] && item.isItemEqual(ingredients[i])) {
                    found[i] = true;
                    foundThisRound = true;
                    break;
                }
            }
            if (!foundThisRound)
                return false;
        }
        // length check done already, no need to repeat
        return true;
    }
}

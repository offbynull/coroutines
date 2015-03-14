package com.offbynull.coroutines.instrumenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassWriter;

final class CustomClassWriter extends ClassWriter {

    private final Map<String, String> superClassMapping;
    
    CustomClassWriter(int flags, Map<String, String> superNameMapping) {
        super(flags);
        Validate.noNullElements(superNameMapping.keySet());
        this.superClassMapping = new HashMap<>(superNameMapping);
    }
    
    /**
     * Derives common super class from the super name mapping put in to the constructor. Does not consult classloader.
     * @param type1 the internal name of a class.
     * @param type2 the internal name of another class.
     * @return the internal name of the common super class of the two given classes
     * @throws IllegalArgumentException if common superclass cannot be found
     */
    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        ArrayList<String> type1Chain = buildTypeChain(type1);
        ArrayList<String> type2Chain = buildTypeChain(type2);
        
        String commonSuperClass = getCommonSuperClassInTypeChains(type1Chain, type2Chain);
        Validate.isTrue(commonSuperClass != null);
        
        return commonSuperClass;
    }
    
    private ArrayList<String> buildTypeChain(String startType) {
        ArrayList<String> typeChain = new ArrayList<>();
        
        typeChain.add(startType);
        while (true) {
            String topType = typeChain.get(typeChain.size() - 1);
            String parentType  = superClassMapping.get(topType);
            if (parentType != null) {
                typeChain.add(parentType);
            } else {
                break;
            }
        }
        
        return typeChain;
    }
    
    private String getCommonSuperClassInTypeChains(ArrayList<String> type1Chain, ArrayList<String> type2Chain) {
        ArrayList<String> intersectionOfTypeChains = new ArrayList<>(type1Chain);
        intersectionOfTypeChains.retainAll(type2Chain);
        
        return intersectionOfTypeChains.isEmpty() ? null : intersectionOfTypeChains.get(0);
    }
}

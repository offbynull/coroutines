package com.offbynull.coroutines.instrumenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.ClassWriter;

/**
 * A {@link ClassWriter} that overrides {@link #getCommonSuperClass(java.lang.String, java.lang.String) } such that it uses a map to find
 * common super classes rather than using the default implementation of loading up the class and using reflections.
 * @author Kasra Faghihi
 */
public final class SimpleClassWriter extends ClassWriter {

    private final Map<String, String> superClassMapping;
    
    /**
     * Constructs a {@link SimpleClassWriter} object.
     * @param flags option flags that can be used to modify the default behavior of this class. See {@link ClassWriter#COMPUTE_MAXS},
     * {@link ClassWriter#COMPUTE_FRAMES}.
     * @param superClassMapping super class mapping (key = internal name of the class, value = the internal name of the class it extends
     * from)
     */
    public SimpleClassWriter(int flags, Map<String, String> superClassMapping) {
        super(flags);
        Validate.noNullElements(superClassMapping.keySet());
        this.superClassMapping = new HashMap<>(superClassMapping);
    }
    
    /**
     * Derives common super class from the super name mapping passed in to the constructor.
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

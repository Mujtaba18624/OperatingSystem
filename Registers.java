package os_phase_2;

import java.io.Serializable;
import java.util.Arrays;


public class Registers implements Serializable {
    
    // General Purpose Registers
    short[] General_Registers ;

     // Special Purpose Registers
    short[] Special_Registers;

    public Registers() {
        
        
        // Zero Register: 0th index in special registers
        // Code base: 1st index in special registers
        // Code Limit: 2nd index in special registers
        // Code Counter: 3rd index in special registers
        // Data Base: 4th index in special registers
        // Data Limit: 5th index in special registers
        // Data Counter: 6th index in special registers
        // Stack Base: 7th index in special registers
        // Stack Counter: 8th index in special registers
        // Stack limit: 9th index in special registers
        // Program Counter: 10th index in special registers
        // Instruction Register: 11th index in special registers
        // Flag Register: 12th index in special registers
        
        General_Registers = new short[16];
        Special_Registers = new short[16];
        
        for(int i=0; i< 16;i++){ 
        General_Registers[i]=0;
        Special_Registers[i]=0;
        }
    }
    @Override
    public String toString() {
        return "Registers{" + "General_"
                + "Registers=" + Arrays.toString(General_Registers) + ", Special_Registers= "
                + "" + Arrays.toString(Special_Registers) + '}';
    }

   
    
}

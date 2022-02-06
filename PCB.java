package os_phase_2;

import java.io.Serializable;
import java.util.ArrayList;

public class PCB implements Serializable, Comparable <PCB> {

    int ProcessID;
    int Process_Priority;
    int Process_Size; //(code+data+segment)
    String Process_Filename = "";
    
    int Waiting_Time;
    int Execution_Time;

    
    // -1 cuz if there is no Code Segment then there will be no Code Pages
    int StartCodePage =-1;
    int EndCodePage = -1;

    // -1 cuz if there is no Data Segment then there will be no Data Pages
    int StartDataPage = -1;
    int EndDataPage = -1;

    int StackFrame ;
    int StackSize = 0;
    

    Registers registers = new Registers();

    ArrayList<Short> Page_TableArray_List = new ArrayList<Short>();

    public PCB() {
        
    }


    @Override
    public String toString() {
        return "PCB{" + "ProcessID= " + ProcessID + ", Process_Priority= " + Process_Priority + ", "
                + "Process_Size= " + Process_Size + ", Process_Filename= " + Process_Filename + "\n"
                + "Waiting_Time= " + Waiting_Time + ", Execution_Time= " + Execution_Time + ", "
                + "StartCodePage= " + StartCodePage + ", EndCodePage= " + EndCodePage + ", "
                + "StartDataPage= " + StartDataPage + ", EndDataPage= " + EndDataPage + "\n"
                + "StackFrame= " + StackFrame + " Stack Size= " + StackSize + "\nRegisters= " + registers + "\n"
                + "Page_TableArray_List= " + Page_TableArray_List.toString() + '}';
    }

    @Override
    public int compareTo(PCB o) {
        return Process_Priority - o.Process_Priority;
    }
}

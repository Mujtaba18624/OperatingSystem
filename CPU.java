package os_phase_2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CPU {

    Registers CPU_Registers;
    Memory Memory_var;
    boolean end;
    boolean terminated = false;

    // Current process in running queue
    PCB RunningProcess;
    int clockCyclesForCurrentProcess = 0;
    // Declaring first ready queue and second ready queue
    PriorityQueue<PCB> FirstReadyQueue;
    PriorityQueue<PCB> SecondReadyQueue;
    // Declaring free frame array
    byte[] FreeFrameArray;

    public CPU() throws IOException, ClassNotFoundException {

        CPU_Registers = new Registers();
        Memory_var = new Memory();
        end = false;

        String inputFile1 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\power";
        String inputFile2 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\flags";
        String inputFile3 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\large0";
        String inputFile4 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\noop";
        String inputFile5 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\p5";
        String inputFile6 = "C:\\Users\\Taha Waseem\\Downloads\\osphase2demofiles\\sfull";

        // Initializing ready queues and Free frame array
        FirstReadyQueue = new PriorityQueue();
        SecondReadyQueue = new PriorityQueue();
        FreeFrameArray = new byte[500];

        String[] Files = {inputFile1, inputFile2, inputFile3, inputFile4, inputFile5, inputFile6};

        // Loading process files into memory
        Load_into_Memory(Files);

        // Geeting process from ready queue and running it as running process
        RunningProcess = getNextProcessToRun();
        CPU_Registers = RunningProcess.registers;

        Decode_Execute();
    }

// Dispatcher performs context switching in this method
    private void contextSwitch() throws ClassNotFoundException, IOException {

        //  storing running queue contents and entering that process back to ready queue
        RunningProcess.registers = CPU_Registers;
        addToQueue(RunningProcess);
        RunningProcess = getNextProcessToRun();

        CPU_Registers = RunningProcess.registers;

    }

    // Terminating a process on either completion of instructions or due to error occurrence
    private void TerminatingProcess() throws ClassNotFoundException, IOException {

        // Freeing frames in Free Frame Array in memory
        for (int i = 0; i < FreeFrameArray.length; i++) {
            FreeFrameArray[i] = 0;
        }
        terminated = true;
        MemoryDump();
        terminated = false;
        if (FirstReadyQueue.size() == 0 && SecondReadyQueue.size() == 0) {
            System.out.println("No more Processes to execute");

            end = true;

        } else {

            // Removing process in running queue and adding new process to it when a process terminates
            RunningProcess = getNextProcessToRun();

            CPU_Registers = RunningProcess.registers;
        }

    }

    // Finding the next process to run after context switch has happend
    private PCB getNextProcessToRun() throws ClassNotFoundException, IOException {

        PriorityQueue<PCB> readyQueue;

        //if first ready queue is not empty then use first ready queue else get second ready queue from memory
        PriorityQueue<PCB> frq = FirstReadyQueue;
        if (frq.isEmpty()) {
            readyQueue = SecondReadyQueue;
        } else {
            readyQueue = frq;
        }

        return readyQueue.poll();
    }

    // Creating and storing PCB contents + storing in memory
    private PCB CreatePCB(String File) throws IOException, ClassNotFoundException {

        PCB process = new PCB();

        try (
                InputStream inputStream = new FileInputStream(File);) {

            int byteRead;

            // Getting File name from path
            int s = 0;
            for (int i = File.length() - 1; i > 0; i--) {
                if (File.charAt(i) == '\\') {
                    s = i;
                    break;
                }
            }

            process.Process_Filename = File.substring(s + 1);
            //System.out.println(process.Process_Filename);
            // Two bytes - used in assigning PID & Data Size
            byte temp1 = 0;
            byte temp2;

            // DataSize variable & count
            int DataSize = 0;
            int count = 1;
            int CurrentFrameToWrite = 0;
            int TrackFrameLimit = 128;
            process.Process_Priority = 0;
            // Boolean flag if process has invalid priority or not
            boolean InvalidPriority = false;

            while ((byteRead = inputStream.read()) != -1) {
                //Assigning Priority
                if (count == 1) {
                    process.Process_Priority = byteRead;
                }
                // Checking if priority is valid
                if (process.Process_Priority < 0 || process.Process_Priority > 31) {
                    InvalidPriority = true;
                    System.out.println("Process: " + process.Process_Filename + " terminated, because it has invalid priority number: " + process.Process_Priority);
                    break;
                }
                //Assigning Process ID
                if (count == 2) {
                    temp1 = (byte) byteRead;
                }
                if (count == 3) {
                    temp2 = (byte) byteRead;
                    int val = ((temp1 & 0xff) << 8) | (temp2 & 0xff);
                    process.ProcessID = val;
                }
                //Assigning Data Size
                if (count == 4) {
                    temp1 = (byte) byteRead;
                }
                if (count == 5) {
                    temp2 = (byte) byteRead;
                    int val = ((temp1 & 0xff) << 8) | (temp2 & 0xff);
                    DataSize = val;
                }
                if (count > 8) {
                    // 1st condition: existing frame full now, find new Free Frame
                    // 2nd condition: Shift to next frame when code segment starts  
                    if (TrackFrameLimit == 128 || (count - 8 == DataSize + 1)) {
                        //To find Free Frames in memory
                        for (int i = 0; i < FreeFrameArray.length; i++) {

                            if (FreeFrameArray[i] == 0) { // if Free page available then
                                //Assigning Starting Page of Data Segment 
                                if (count == 9 && DataSize >= 1) {
                                    process.StartDataPage = i;
                                    // Assigning data base register
                                    process.registers.Special_Registers[4] = (short) (i * 128);
                                } // If datasize is 0 then code segment starts from 9th byte
                                else if (count == 9 && DataSize == 0) {

                                    // Assigning Code Base register and Program Counter
                                    process.registers.Special_Registers[1] = (short) (i * 128);
                                    process.registers.Special_Registers[10] = (short) (i * 128);
                                    process.StartCodePage = i;

                                } // Assigning Start Code Page & End Data Page 
                                else if (count - 8 == DataSize + 1) {
                                    process.EndDataPage = CurrentFrameToWrite;

                                    // Assigning data counter and data limit register 
                                    process.registers.Special_Registers[6] = (short) ((CurrentFrameToWrite * 128) + TrackFrameLimit);
                                    process.registers.Special_Registers[5] = (short) (process.registers.Special_Registers[6] - process.registers.Special_Registers[4]);

                                    // Assigning code base register and Program Counter
                                    process.registers.Special_Registers[1] = (short) (i * 128);
                                    process.registers.Special_Registers[10] = (short) (i * 128);

                                    process.StartCodePage = i;
                                }
                                // Initializing tracker
                                TrackFrameLimit = 0;

                                process.Page_TableArray_List.add((short) i); // Assigning frames to page table
                                CurrentFrameToWrite = i;
                                FreeFrameArray[i] = 1;  // Frame has been written on
                                break; // Move to writing on memory now
                            }
                        }
                    }
                    int MemoryIndex = (CurrentFrameToWrite * 128) + TrackFrameLimit;
                    byte enterMemory = (byte) byteRead;
                    Memory_var.Memory[MemoryIndex] = enterMemory;
                    TrackFrameLimit++;
                }
                count++;
            }//While loop ends here

            if (InvalidPriority == false) {
                // Assigning End Code Page
                process.EndCodePage = CurrentFrameToWrite;

                // Assigning Code counter and Code Limit register
                process.registers.Special_Registers[3] = (short) ((CurrentFrameToWrite * 128) + (TrackFrameLimit % 128));
                process.registers.Special_Registers[2] = (short) (process.registers.Special_Registers[3] - process.registers.Special_Registers[1]);

                process.Process_Size = count - 9;

                // Assigning Stack Frame
                for (int i = 0; i < FreeFrameArray.length; i++) {
                    if (FreeFrameArray[i] == 0) { // if Free page available then
                        process.StackFrame = i;
                        process.registers.Special_Registers[7] = (short) (128 * i);
                        break;
                    }
                }

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return process;
    }

    // Adding PCB to second ready queue after priority is validated
    private void addToSecondReadyQueue(PCB pcb) throws IOException, ClassNotFoundException {
        // Adding pcb to second ready queue
        SecondReadyQueue.add(pcb);
    }

    // Adding a PCB to Queue in memory
    private void addToQueue(PCB pcb) throws ClassNotFoundException, IOException {
        if (pcb.Process_Priority < 16 && pcb.Process_Priority >= 0) {
            addToFirstReadyQueue(pcb);
        } else if (pcb.Process_Priority < 32 && pcb.Process_Priority >= 16) {
            addToSecondReadyQueue(pcb);
        }

    }

    // Adding PCB to first ready queue after priority is validated
    private void addToFirstReadyQueue(PCB pcb) throws IOException, ClassNotFoundException {
        // Adding pcb to second ready queue
        FirstReadyQueue.add(pcb);
    }

    // Reading process files, creating their PCB and storing them in memory's ready queue 
    private void Load_into_Memory(String[] array) throws ClassNotFoundException, IOException {

        // Taking array of strings as filename that contains process and creating their pcb
        for (int i = 0; i < array.length; i++) {
            addToQueue(CreatePCB(array[i]));
        }

    }

    private void JumpPcIncrement() throws ClassNotFoundException, IOException {
        short PC = CPU_Registers.Special_Registers[10];
        short PS = 128; //PageSize

        short currentFrame;

        currentFrame = (short) (PC / PS);
        int i = 0;
        for (i = 0; i < RunningProcess.Page_TableArray_List.size(); i++) {
            if (RunningProcess.Page_TableArray_List.get(i) == currentFrame) {
                break;
            }
        }

        short getCurrentPage = (short) i;
        short currentOffset = (short) (PC % PS);

        short immediate = Get_Immediate();

        short JumpPages = (short) (immediate / PS);
        short JumpOffset = (short) (immediate % PS);

        short CombinedOffset = (short) (currentOffset + JumpOffset);

        if (CombinedOffset >= PS) {
            JumpPages = (short) (JumpPages + 1);
            CombinedOffset = (short) (CombinedOffset % PS);
        }

        short PageToJump = (short) (getCurrentPage + JumpPages);
        short getFrame = 0;

        // Checking if frame exists for that Page Number in Page Table
        if (PageToJump > RunningProcess.Page_TableArray_List.size() - 1) {
            // Getting the corresponding frame from page table
            getFrame = RunningProcess.Page_TableArray_List.get(PageToJump);
            CPU_Registers.Special_Registers[10] = (short) ((getFrame * PS) + CombinedOffset);
        } else {
            System.out.println("Access Violation, process " + RunningProcess.Process_Filename + " terminated");
            TerminatingProcess();
        }

    }

    private void Increment_PC() {

        int pc = CPU_Registers.Special_Registers[10];

        // Incrementing Program Counter value based on Instruction Type (opcode stored in IR)
        if (CPU_Registers.Special_Registers[11] >= 22 && CPU_Registers.Special_Registers[11] <= 28) {
            pc = pc + 3;
        } else if (CPU_Registers.Special_Registers[11] >= 48 && CPU_Registers.Special_Registers[11] <= 61) {
            pc = pc + 4;
        } else if (CPU_Registers.Special_Registers[11] == 82 || CPU_Registers.Special_Registers[11] == 81) {
            pc = pc + 4;
        } else if (CPU_Registers.Special_Registers[11] >= 113 && CPU_Registers.Special_Registers[11] <= 120) {
            pc = pc + 2;
        } else if (CPU_Registers.Special_Registers[11] == 241 || CPU_Registers.Special_Registers[11] == 242 || CPU_Registers.Special_Registers[11] == 243) {
            pc = pc + 1;
        }

        // Setting Program Counter value
        CPU_Registers.Special_Registers[10] = (short) pc;

    }

    // Fetching instruction from memory and then executing the instruction by calling their methods
    private void Decode_Execute() throws ClassNotFoundException, IOException {

        while (end == false) {

            // For updating execution time of terminated process
            boolean ExecutionTime = false;

            if (clockCyclesForCurrentProcess == 8) {
                contextSwitch();
            } else {
                clockCyclesForCurrentProcess += 2;
            }

            // Loading the IR Register with the OPCODE (from memory) using PC
            CPU_Registers.Special_Registers[11] = (short) Byte.toUnsignedInt(Memory_var.Memory[CPU_Registers.Special_Registers[10]]);

            // OPCODE is just a variable that has the OPCODE value
            int OPCODE = CPU_Registers.Special_Registers[11];
            if (OPCODE == 22) {
                MOV();        // Calling MOV instruction function
            } else if (OPCODE == 23) {
                ADD();         // Calling ADD instruction function
            } else if (OPCODE == 24) {
                SUB();         // Calling SUB instruction function
            } else if (OPCODE == 25) {
                MUL();         // Calling MUL instruction function
            } else if (OPCODE == 26) {
                DIV();         // Calling DIV instruction function
            } else if (OPCODE == 27) {
                AND();         // Calling AND instruction function
            } else if (OPCODE == 28) {
                OR();         // Calling OR instruction function
            } else if (OPCODE == 48) {
                MOVI();         // Calling MOVI instruction function
            } else if (OPCODE == 49) {
                ADDI();         // Calling ADDI instruction function
            } else if (OPCODE == 50) {
                SUBI();         // Calling SUBI instruction function
            } else if (OPCODE == 51) {
                DIVI();         // Calling DIVI instruction function
            } else if (OPCODE == 52) {
                ANDI();         // Calling ANDI instruction function
            } else if (OPCODE == 53) {
                ORI();         // Calling ORI instruction function
            } else if (OPCODE == 54) {
                BZ();         // Calling BZ instruction function
            } else if (OPCODE == 55) {
                BNZ();         // Calling BNZ instruction function
            } else if (OPCODE == 56) {
                BC();         // Calling BC instruction function
            } else if (OPCODE == 57) {
                BS();         // Calling BS instruction function
            } else if (OPCODE == 58) {
                JMP();         // Calling JMP instruction function
            } else if (OPCODE == 60) {
                CALL();         // Calling CALL instruction function
            } else if (OPCODE == 61) {
                ACT();         // Calling ACT instruction function
            } else if (OPCODE == 48) {
                MOVI();         // Calling MOVI instruction function
            } else if (OPCODE == 81) {
                MOVL();         // Calling MOVL instruction function
            } else if (OPCODE == 82) {
                MOVS();         // Calling MOVS instruction function
            } else if (OPCODE == 113) {
                SHL();         // Calling SHL instruction function
            } else if (OPCODE == 114) {
                SHR();         // Calling SHR instruction function
            } else if (OPCODE == 115) {
                RTL();         // Calling RTL instruction function
            } else if (OPCODE == 116) {
                RTR();         // Calling RTR instruction function
            } else if (OPCODE == 117) {
                INC();         // Calling INC instruction function
            } else if (OPCODE == 118) {
                DEC();         // Calling DEC instruction function
            } else if (OPCODE == 119) {
                PUSH();         // Calling PUSH instruction function
            } else if (OPCODE == 120) {
                POP();         // Calling POP instruction function
            } else if (OPCODE == 241) {
                RETURN();         // Calling RETURN instruction function
            } else if (OPCODE == 242) {
                NOOP();         // Calling NOOP instruction function
            } else if (OPCODE == 243) {
                System.out.println("Prcoess " + RunningProcess.Process_Filename + " succesfully completed. F3 instruction.");
                ExecutionTime = true;
                RunningProcess.Execution_Time += 2;
                UpdateWaitingTime();
                END();
                // Terminating process means writing memory dump on file and removing current process from queue and context switching called
            } // Stop looping if PC GREATER THAN CC
            else if (CPU_Registers.Special_Registers[10] >= CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation , process " + RunningProcess.Process_Filename + " terminated");
                ExecutionTime = true;
                RunningProcess.Execution_Time += 2;
                UpdateWaitingTime();
                END();
            } else {
                System.out.println("Invalid Opcode: " + OPCODE + " , process " + RunningProcess.Process_Filename + " terminated");
                ExecutionTime = true;
                RunningProcess.Execution_Time += 2;
                UpdateWaitingTime();
                END();
            }

            //if not jump instruction
            if (OPCODE > 54 && OPCODE < 61) {

            } else if (OPCODE == 241) {

            } else {
                Increment_PC();
            }

            if (!ExecutionTime) {
                RunningProcess.Execution_Time += 2;
                UpdateWaitingTime();
            }

            // Calling Memory Dump after each instruction
            MemoryDump();
        }
    }

    private void UpdateWaitingTime() {

        // iterate the Priority Queue
        for (PCB element : SecondReadyQueue) {
            element.Waiting_Time += 2;
        }
        for (PCB element : FirstReadyQueue) {
            element.Waiting_Time += 2;
        }

    }

    // Converting Hexa Decimal string to Decimal value
    public static int getDecimal(String hex) {
        String digits = "0123456789ABCDEF";
        hex = hex.toUpperCase();
        int val = 0;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            int d = digits.indexOf(c);
            val = 16 * val + d;
        }
        return val;
    }

    // Function to get 16 bit immediate value from memory
    private short Get_Immediate() {

        short immediate = (short) Byte.toUnsignedInt(Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2]);  //Storing first 8 bits of immediate
        immediate = (short) (immediate << 8); // shifting left by 8 bits
        immediate += (short) Byte.toUnsignedInt(Memory_var.Memory[CPU_Registers.Special_Registers[10] + 3]); //adding 8 bits more from memory to immediate

        return immediate;
    }

    // Adding Zeros to fulfill 16 bits
    private char[] Buffing_Bits(short a) {

        String binaryString = Integer.toBinaryString(a);

        int len = binaryString.length();
        String c = "";

        // Adding the remaining no. of zeroes to complete the string
        for (int i = 0; i < 16 - len; i++) {
            c += "0";
        }
        c += binaryString;

        return c.toCharArray();
    }

    private void OVERFLOW_FLAG(int result) {

        // Getting 16 bit binary character array for flag register
        char[] flag_array = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Updating overflow bit by checking conditions
        // Maximum value of short = 32767
        // Minimum value of short = -32768
        if (result > 32767 || result < -32768) {
            flag_array[12] = '1';
        } else if (result >= -32768 && result <= 32767) {
            flag_array[12] = '0';
        }

        //Converting character array to binary string
        String s = String.valueOf(flag_array);

        // Converting binary string to decimal and stroing that value in flag register
        CPU_Registers.Special_Registers[12] = (short) Integer.parseInt(s, 2);
    }

    private void ZERO_SIGN_FLAG(int result) {

        // Getting 16 bit binary character array for flag register
        char[] flag_array = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Updating Zero bit using conditions
        if (result == 0) {
            flag_array[14] = '1'; // setting zero bit to 1
        } else if (result != 0) {
            flag_array[14] = '0'; // resetting zero bit to 0
        }

        // Updating Sign bit using conditions
        if (result < 0) {
            flag_array[13] = '1';
        } else if (result >= 0) {
            flag_array[13] = '0';
        }

        //Converting character array to binary string
        String s = String.valueOf(flag_array);

        // Converting binary string to decimal and stroing that value in flag register
        CPU_Registers.Special_Registers[12] = (short) Integer.parseInt(s, 2);
    }

    private void CHECK_CARRY_FLAG(char[] r1) {

        // Flag Register in byte form
        char[] flag_array = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Checking MSB and updating Carry bit accordingly
        if (r1[0] == '1') {
            flag_array[15] = '1';
        } else if (r1[0] == '0') {
            flag_array[15] = '0';
        }

        //Converting flag character array to binary string
        String s = String.valueOf(flag_array);

        // Converting binary string to decimal and stroing that value in flag register
        CPU_Registers.Special_Registers[12] = (short) Integer.parseInt(s, 2);
    }

    private void NOOP() {
        System.out.println("No Operation");
    }

    private void RETURN() throws ClassNotFoundException, IOException {

        if (RunningProcess.StackSize == 0) {
            System.out.println("Stack Underflow, process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {

            int StartingStackFrameByte = RunningProcess.StackFrame * 128;
            // Getting register content from Stack
            byte firstByte = Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize - 2];
            byte secondByte = Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize - 1];

            short pc = (short) (((firstByte & 0xff) << 8) | (secondByte & 0xff));

            CPU_Registers.Special_Registers[10] = pc;

            RunningProcess.StackSize = RunningProcess.StackSize - 2;
        }

    }

    private void POP() throws ClassNotFoundException, IOException {

        if (RunningProcess.StackSize <= 0) {
            System.out.println("Stack Underflow, process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {

            int StartingStackFrameByte = RunningProcess.StackFrame * 128;

            // Getting register content from Stack
            byte firstByte = Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize - 2];
            byte secondByte = Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize - 1];
            short content = (short) (((firstByte & 0xff) << 8) | (secondByte & 0xff));

            int R1_code;
            // REGISTER CODE IN BYTE FORM
            R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

            CPU_Registers.General_Registers[R1_code] = content;
            RunningProcess.StackSize = RunningProcess.StackSize - 2;

        }

    }

    private void PUSH() throws ClassNotFoundException, IOException {

        if (RunningProcess.StackSize == 50) {
            System.out.println("Stack Overflow, process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {

            int R1_code;
            // REGISTER CODE IN BYTE FORM
            R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
            short R1_c = CPU_Registers.General_Registers[R1_code];

            // Pushing PC onto stack
            int StartingStackFrameByte = RunningProcess.StackFrame * 128;

            byte byte1 = (byte) ((R1_c & 0xff00) >> 8);
            byte byte2 = (byte) (R1_c & 0xff);

            Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize] = byte1;
            Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize + 1] = byte2;
            RunningProcess.StackSize = RunningProcess.StackSize + 2;
        }
    }

    private void DEC() {

        int R1_code;

        // REGISTER CODE IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Decrementing
        int result = (CPU_Registers.General_Registers[R1_code] - 1);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void INC() {
        int R1_code;

        // REGISTER CODE IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Incrementing
        int result = (CPU_Registers.General_Registers[R1_code] + 1);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void RTL() {

        // REGISTER CODE IN BYTE FORM
        int R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting register value
        short n = CPU_Registers.General_Registers[R1_code];

        char[] R1_Array = Buffing_Bits(n);

        // Checking carry bit
        CHECK_CARRY_FLAG(R1_Array);

        // Rotating Left
        int result = ((n << 1) | (n >> (16 - 1)));

        // Updating Zero bit and sign bit using function ZERO_SIGN_FLAG() FUNCTION
        ZERO_SIGN_FLAG(result);

        //Saving value back in register
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void RTR() {

        // REGISTER CODE IN BYTE FORM
        int R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting register value
        short n = CPU_Registers.General_Registers[R1_code];

        char[] R1_Array = Buffing_Bits(n);

        // Checking carry bit
        CHECK_CARRY_FLAG(R1_Array);

        // Rotating Right
        int result = ((n >> 1) | (n << (16 - 1)));

        // Updating Zero bit and sign bit using function ZERO_SIGN_FLAG() FUNCTION
        ZERO_SIGN_FLAG(result);

        //Saving value back in register
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void SHR() {

        // REGISTER CODE IN BYTE FORM
        int R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        // Getting register value
        short n = CPU_Registers.General_Registers[R1_code];

        // R1 value in byte form
        char[] R1_Array = Buffing_Bits(n);

        // Checking carry bit
        CHECK_CARRY_FLAG(R1_Array);

        //shifting right
        int result = (CPU_Registers.General_Registers[R1_code] >> 1);

        // Updating zero and sign bit
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void SHL() {
        // REGISTER CODE IN BYTE FORM
        int R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        // Getting register value
        short n = CPU_Registers.General_Registers[R1_code];

        char[] R1_Array = Buffing_Bits(n);

        // Checking carry bit
        CHECK_CARRY_FLAG(R1_Array);

        //shifting left
        int result = (CPU_Registers.General_Registers[R1_code] << 1);

        // Updating zero and sign bit
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void MOVS() throws ClassNotFoundException, IOException {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // Storing word into memory from register
        Memory_var.Memory[immediate + CPU_Registers.Special_Registers[4]] = (byte) CPU_Registers.General_Registers[R1_code];

        if (CPU_Registers.Special_Registers[4] > CPU_Registers.Special_Registers[5]) {
            System.out.println("Access Violation ,process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {

        }

    }

    private void MOVL() throws ClassNotFoundException, IOException {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // Loading word into register from memory
        CPU_Registers.General_Registers[R1_code] = Memory_var.Memory[immediate + CPU_Registers.Special_Registers[4]];

        if (CPU_Registers.Special_Registers[4] > CPU_Registers.Special_Registers[5]) {
            System.out.println("Access Violation, process " + RunningProcess.Process_Filename + " terminated");
            END();
        }
    }

    private void MOVI() {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // Moving immediate value to Register R1 
        CPU_Registers.General_Registers[R1_code] = (short) (immediate);
    }

    private void ACT() {
        System.out.println("Not Implemented Yet");
    }

    private void CALL() throws ClassNotFoundException, IOException {
        short immediate = Get_Immediate();

        if (RunningProcess.StackSize == 50) {
            System.out.println("Stack Overflow, process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {
            // Pushing PC onto stack
            byte StartingStackFrameByte = (byte) (RunningProcess.StackFrame * 128);
            CPU_Registers.Special_Registers[10] = (short) ((int) CPU_Registers.Special_Registers[10] + 4);

            byte byte1 = (byte) ((CPU_Registers.Special_Registers[10] & 0xff00) >> 8);
            byte byte2 = (byte) (CPU_Registers.Special_Registers[10] & 0xff);
            Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize] = byte1;
            Memory_var.Memory[StartingStackFrameByte + RunningProcess.StackSize + 1] = byte2;
            RunningProcess.StackSize = RunningProcess.StackSize + 2;

            //setting PC to Code Base + immediate
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);

            if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation, process " + RunningProcess.Process_Filename + " terminated");
                END();
            }
        }
    }

    private void JMP() throws ClassNotFoundException, IOException {

        short immediate = Get_Immediate();

        //setting PC to Code Base + immediate
        CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);
        JumpPcIncrement();

        if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
            System.out.println("Access Violation , process " + RunningProcess.Process_Filename + " terminated");
            END();
        }

    }

    private void END() throws ClassNotFoundException, IOException {

        TerminatingProcess();
    }

    private void BS() throws ClassNotFoundException, IOException { //Branch if sign

        // Converting value of Flag Register into 16 bit Binary character array
        char[] binary = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Checking Sign bit is 1
        if (binary[13] == '1') {

            // Getting 16 bits immediate from memory
            short immediate = Get_Immediate();

            //Setting PC to Code Base + Immediate
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);
            JumpPcIncrement();

            if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation , process " + RunningProcess.Process_Filename + " terminated");
                END();
            }
        } else {
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[10] + 4);
        }
    }

    private void BC() throws ClassNotFoundException, IOException { //Branch if carry

        // Converting value of Flag Register into 16 bit Binary character array
        char[] binary = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Checking Carry bit is 1
        if (binary[15] == '1') {

            // Getting 16 bits immediate from memory
            short immediate = Get_Immediate();

            //Setting PC to Code Base + Immediate
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);
            JumpPcIncrement();

            if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation, process " + RunningProcess.Process_Filename + " terminated");
                END();
            }
        } else {

            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[10] + 4);
        }
    }

    private void BNZ() throws ClassNotFoundException, IOException { // Branch if not zero, BNZ Num

        // Converting value of Flag Register into 16 bit Binary character array
        char[] binary = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Checking Zero bit is 0
        if (binary[14] == '0') {

            // Getting 16 bits immediate from memory
            short immediate = Get_Immediate();

            //Setting PC to Code Base + Immediate
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);
            JumpPcIncrement();

            if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation , process " + RunningProcess.Process_Filename + " terminated");
                END();
            }
        } else {

            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[10] + 4);
        }
    }

    private void BZ() throws ClassNotFoundException, IOException { //  Branch equal to zero, BZ Num

        // Converting value of Flag Register into 16 bit Binary character array
        char[] binary = Buffing_Bits(CPU_Registers.Special_Registers[12]);

        // Checking Zero bit is 1
        if (binary[14] == '1') {

            // Getting 16 bits immediate from memory
            short immediate = Get_Immediate();

            //Setting PC to Code Base + Immediate
            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[1] + immediate);
            JumpPcIncrement();

            if (CPU_Registers.Special_Registers[10] > CPU_Registers.Special_Registers[3]) {
                System.out.println("Access Violation , process " + RunningProcess.Process_Filename + " terminated");
                END();
            }
        } else {

            CPU_Registers.Special_Registers[10] = (short) (CPU_Registers.Special_Registers[10] + 4);
        }
    }

    private void ORI() {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // OR immediate value to Register R1 value 
        int result = (CPU_Registers.General_Registers[R1_code] | immediate);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void ANDI() {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // AND immediate value to Register R1 value
        int result = (CPU_Registers.General_Registers[R1_code] & immediate);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void DIVI() throws ClassNotFoundException, IOException {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        if (immediate == 0) {
            System.out.println("Divide by Zero Error , process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {

            // Dividing immediate value to Register R1 value
            int result = (CPU_Registers.General_Registers[R1_code] / immediate);

            //  Updating overflow bit using OVERFLOW_FLAG() function
            OVERFLOW_FLAG(result);
            // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
            ZERO_SIGN_FLAG(result);

            // Saving result in R1
            CPU_Registers.General_Registers[R1_code] = (short) result;
        }
    }

    private void SUBI() {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // Subtracting immediate value to Register R1 value 
        int result = (CPU_Registers.General_Registers[R1_code] - immediate);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void ADDI() {

        int R1_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];

        // Getting 16 bits immediate from memory
        short immediate = Get_Immediate();

        // Adding immediate value to Register R1 value
        int result = (CPU_Registers.General_Registers[R1_code] + immediate);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void MOV() {
        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // Moving register R1 value with register R2 and saving it in R1
        CPU_Registers.General_Registers[R1_code] = CPU_Registers.General_Registers[R2_code];
    }

    private void OR() {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // OR register R1 value with register R2 value and 
        int result = (CPU_Registers.General_Registers[R1_code] | CPU_Registers.General_Registers[R2_code]);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void AND() {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // AND register R1 value with register R2 value
        int result = (CPU_Registers.General_Registers[R1_code] & CPU_Registers.General_Registers[R2_code]);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);
        ;

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void DIV() throws ClassNotFoundException, IOException {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // Divide register R1 value with register R2 value
        if (CPU_Registers.General_Registers[R2_code] == 0) {
            System.out.println("Divide by Zero Error , process " + RunningProcess.Process_Filename + " terminated");
            END();
        } else {
            int result = (CPU_Registers.General_Registers[R1_code] / CPU_Registers.General_Registers[R2_code]);

            //  Updating overflow bit using OVERFLOW_FLAG() function
            OVERFLOW_FLAG(result);
            // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
            ZERO_SIGN_FLAG(result);

            // Saving result in R1
            CPU_Registers.General_Registers[R1_code] = (short) result;
        }
    }

    private void MUL() {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // Multiply R1 VALUE WITH R2 VALUE
        int result = (CPU_Registers.General_Registers[R1_code] * CPU_Registers.General_Registers[R2_code]);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void SUB() {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // Subtract register R1 value with register R2 value
        int result = (CPU_Registers.General_Registers[R1_code] - CPU_Registers.General_Registers[R2_code]);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;
    }

    private void ADD() {

        int R1_code;
        int R2_code;

        // REGISTER CODES IN BYTE FORM
        R1_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 1];
        R2_code = Memory_var.Memory[CPU_Registers.Special_Registers[10] + 2];

        // Add register R1 value with register R2 value
        int result = (CPU_Registers.General_Registers[R1_code] + CPU_Registers.General_Registers[R2_code]);

        //  Updating overflow bit using OVERFLOW_FLAG() function
        OVERFLOW_FLAG(result);
        // Updating Zero bit and Sign bit using ZERO_SIGN_FLAG() function
        ZERO_SIGN_FLAG(result);

        // Saving result in R1
        CPU_Registers.General_Registers[R1_code] = (short) result;

    }

    private void MemoryDump() {
        File output = new File("MemoryDump.txt");
        FileWriter writer;
        try {
            writer = new FileWriter(output, true);
            String print = RunningProcess.toString();

            print += "\nData Segment:\n";

            for (int i = 0; i < RunningProcess.registers.Special_Registers[5]; i++) {
                print += "Memory Index " + RunningProcess.registers.Special_Registers[4] + i + ": ";
                print += Memory_var.Memory[RunningProcess.registers.Special_Registers[4] + i] + ", ";
            }

            print += "\nCode Segment:\n";
            for (int i = 0; i < RunningProcess.registers.Special_Registers[2]; i++) {
                print += "Memory Index " + RunningProcess.registers.Special_Registers[1] + i + ": ";
                print += Memory_var.Memory[RunningProcess.registers.Special_Registers[1] + i] + ", ";
            }

            print += "\nStack Contents:\n";
            int temp = RunningProcess.StackFrame * 128;
            for (int i = 0; i < 50; i++) {
                print += "Memory Index " + temp + i + ": ";
                print += Memory_var.Memory[temp + i] + ", ";
            }

            if (terminated) {
                print = print + "\n";
                System.out.println(print);
            }

            writer.write(print);
            writer.flush();
            writer.close();

        } catch (IOException ex) {
            Logger.getLogger(CPU.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

}

package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
 * The MMU class contains the student code that performs the work of handling a
 * memory reference. It is responsible for calling the interrupt handler if a
 * page fault is required.
 * 
 * @OSPProject Memory
 */
public class MMU extends IflMMU {
	// I think they have to private not public. page 92
	public static int Cursor;
	public static int wantFree;

	/**
	 * This method is called once before the simulation starts. Can be used to
	 * initialize the frame table and other static variables.
	 * 
	 * @OSPProject Memory
	 * 
	 *             This method is called once, at the beginning, to initialize the
	 *             data structures. Typically, it is used to initialize the frame
	 *             table. Since the total number of frames is known
	 *             (MMU.getFrameTableSize()), each frame in the frame table can be
	 *             initialized in a for-loop. Initially, all entries in the frame
	 *             table are just null-objects and must be set to real frame table
	 *             objects using the FrameTableEntry() constructor. To set a frame
	 *             entry, use the method setFrame() in class MMU. Another use of the
	 *             init() method is for the initialization of private static
	 *             variables defined in other classes of the Memory package. For
	 *             example, one can define an init() method in class
	 *             PageFaultHandler which would be able to access any variable
	 *             defined in that class. Then MMU.init() can call
	 *             PageFaultHandler.init(). Since MMU.init() is called at the very
	 *             begin- ning of the simulation, PageFaultHandler.init() is also
	 *             going to be called at the beginning of the simulation.
	 */

	public static void init() {
		Cursor = 0;
		wantFree = 1;

		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			MMU.setFrame(i, new FrameTableEntry(i));
		}
	}

	/**
	 * This method handles memory references. The method must calculate, which
	 * memory page contains the memoryAddress, determine, whether the page is valid,
	 * start page fault by making an interrupt if the page is invalid, finally, if
	 * the page is still valid, i.e., not swapped out by another thread while this
	 * thread was suspended, set its frame as referenced and then set it as dirty if
	 * necessary. (After pagefault, the thread will be placed on the ready queue,
	 * and it is possible that some other thread will take away the frame.)
	 * 
	 * @param memoryAddress A virtual memory address
	 * @param referenceType The type of memory reference to perform
	 * @param thread        that does the memory access (e.g., MemoryRead or
	 *                      MemoryWrite).
	 * @return The referenced page.
	 * 
	 * @OSPProject Memory
	 */
	static public PageTableEntry do_refer(int memoryAddress, int referenceType, ThreadCB thread) {
//		int VABits = MMU.getVirtualAddressBits();
//		int PBits = MMU.getPageAddressBits();
//		int DBits = VABits - PBits;
//		int PageSize = (int) Math.pow(2.0, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());
		int PageNum = memoryAddress / (int) Math.pow(2.0, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());

		PageTableEntry PTE = getPTBR().pages[PageNum];
//    	PageTableEntry PTE = thread.getTask().getPageTable().pages[PageNum];
		if (PTE.isValid()) {
			PTE.getFrame().setReferenced(true);
			if (referenceType == GlobalVariables.MemoryWrite)
				PTE.getFrame().setDirty(true);
			return PTE;
		}

		else {
			if (PTE.getValidatingThread() == null) {
				InterruptVector.setInterruptType(referenceType);
				InterruptVector.setPage(PTE);
				InterruptVector.setThread(thread);
				CPU.interrupt(PageFault);
				if (thread.getStatus() != GlobalVariables.ThreadKill) {
					PTE.getFrame().setReferenced(true);
					if (referenceType == GlobalVariables.MemoryWrite)
						PTE.getFrame().setDirty(true);
					return PTE;
				}
				else
					return PTE;
			} 
			
			else {
				thread.suspend(PTE);
				if (thread.getStatus() != GlobalVariables.ThreadKill) {
					PTE.getFrame().setReferenced(true);
					if (referenceType == GlobalVariables.MemoryWrite)
						PTE.getFrame().setDirty(true);
					return PTE;
				}
				
				else {
					return PTE;
				}
			}
		}
	}

	/**
	 * Called by OSP after printing an error message. The student can insert code
	 * here to print various tables and data structures in their state just after
	 * the error happened. The body can be left empty, if this feature is not used.
	 * 
	 * @OSPProject Memory
	 */
	public static void atError() {
		// your code goes here (if needed)

	}

	/**
	 * Called by OSP after printing a warning message. The student can insert code
	 * here to print various tables and data structures in their state just after
	 * the warning happened. The body can be left empty, if this feature is not
	 * used.
	 * 
	 * @OSPProject Memory
	 */
	public static void atWarning() {
		// your code goes here (if needed)

	}
}
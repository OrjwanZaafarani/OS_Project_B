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

	// Global Variables to be used by SecondChance()
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

	// Authors: ID:
	// Orjwan Zaafarani 1506807
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 4/4/2020
	// Initialization function called once only during simulation
	public static void init() {

		// Initialize cursor to 0 (for MMU iteration) & wantFree (for freeing frames) to
		// 1
		Cursor = 0;
		wantFree = 1;

		// Setting frames for Frame Table
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

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 12/4/2020
	// Handles memory references
	static public PageTableEntry do_refer(int memoryAddress, int referenceType, ThreadCB thread) {
		// Calculates Page Number
		int PageNum = memoryAddress / (int) Math.pow(2.0, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());

		// Accesses PageTable to get page of calculated number
		PageTableEntry PTE = getPTBR().pages[PageNum];

		// Check page's validity --> if valid, set referenced and dirty bits accordingly
		if (PTE.isValid()) {
			PTE.getFrame().setReferenced(true);
			if (referenceType == GlobalVariables.MemoryWrite)
				PTE.getFrame().setDirty(true);
			return PTE;
		}

		// Page not valid
		else {

			// Page does not have a validating thread
			if (PTE.getValidatingThread() == null) {
				// Configure interrupt attributes
				InterruptVector.setInterruptType(referenceType);
				InterruptVector.setPage(PTE);
				InterruptVector.setThread(thread);

				// Cause a PageFault interrupt
				CPU.interrupt(PageFault);

				// Check thread status after interrupt --> if not killed, set referenced and
				// dirty bits accordingly
				if (thread.getStatus() != GlobalVariables.ThreadKill) {
					PTE.getFrame().setReferenced(true);
					if (referenceType == GlobalVariables.MemoryWrite)
						PTE.getFrame().setDirty(true);
					return PTE;
				}

				// If killed, do not change referenced and dirty bits
				else
					return PTE;
			}

			// Page has a validating thread
			else {

				// Suspend thread to wait for validating thread to handle page fault
				thread.suspend(PTE);

				// Check thread status after suspension --> if not killed, set referenced and
				// dirty bits accordingly
				if (thread.getStatus() != GlobalVariables.ThreadKill) {
					PTE.getFrame().setReferenced(true);
					if (referenceType == GlobalVariables.MemoryWrite)
						PTE.getFrame().setDirty(true);
					return PTE;
				}

				// If killed, do not change referenced and dirty bits
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
	}
}
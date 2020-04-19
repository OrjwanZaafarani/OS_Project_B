package osp.Memory;

import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import java.lang.*;

/**
 * The page fault handler is responsible for handling a page fault. If a swap in
 * or swap out operation is required, the page fault handler must request the
 * operation.
 * 
 * @OSPProject Memory
 */
public class PageFaultHandler extends IflPageFaultHandler {
	// User Option for debugging and testing purposes
	// static String userOption = "s";

	// Creating a Global Variable to keep count of Page Faults
	static int numPageFaults = 0;

	/**
	 * This method handles a page fault.
	 * 
	 * It must check and return if the page is valid,
	 * 
	 * It must check if the page is already being brought in by some other thread,
	 * i.e., if the page has already pagefaulted (for instance, using
	 * getValidatingThread()). If that is the case, the thread must be suspended on
	 * that page.
	 * 
	 * If none of the above is true, a new frame must be chosen and reserved until
	 * the swap in of the requested page into this frame is complete.
	 * 
	 * Note that you have to make sure that the validating thread of a page is set
	 * correctly. To this end, you must set the page's validating thread using
	 * setValidatingThread() when a pagefault happens and you must set it back to
	 * null when the pagefault is over.
	 * 
	 * If no free frame could be found, then a page replacement algorithm must be
	 * used to select a victim page to be replaced.
	 * 
	 * If a swap-out is necessary (because the chosen frame is dirty), the victim
	 * page must be dissasociated from the frame and marked invalid. After the
	 * swap-in, the frame must be marked clean. The swap-ins and swap-outs must be
	 * preformed using regular calls to read() and write().
	 * 
	 * The student implementation should define additional methods, e.g, a method to
	 * search for an available frame, and a method to select a victim page making
	 * its frame available.
	 * 
	 * Note: multiple threads might be waiting for completion of the page fault. The
	 * thread that initiated the pagefault would be waiting on the IORBs that are
	 * tasked to bring the page in (and to free the frame during the swapout).
	 * However, while pagefault is in progress, other threads might request the same
	 * page. Those threads won't cause another pagefault, of course, but they would
	 * enqueue themselves on the page (a page is also an Event!), waiting for the
	 * completion of the original pagefault. It is thus important to call
	 * notifyThreads() on the page at the end -- regardless of whether the pagefault
	 * succeeded in bringing the page in or not.
	 * 
	 * @param thread        the thread that requested a page fault
	 * @param referenceType whether it is memory read or write
	 * @param page          the memory page
	 * 
	 * @return SUCCESS is everything is fine; FAILURE if the thread dies while
	 *         waiting for swap in or swap out or if the page is already in memory
	 *         and no page fault was necessary (well, this shouldn't happen,
	 *         but...). In addition, if there is no frame that can be allocated to
	 *         satisfy the page fault, then it should return NotEnoughMemory
	 * 
	 * @OSPProject Memory
	 */
	// Authors: ID:
	// Orjwan Zaafarani 1506807
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 19/4/2020
	// Page Fault Handling Method
	public static int do_handlePageFault(ThreadCB thread, int referenceType, PageTableEntry page) {
		// Counter to iterate through memory --> counts reserved or locked frames
		int counter = 0;
		FrameTableEntry frame;

		// Check if page is valid --> if so return failure
		if (page.isValid()) {
			page.notifyThreads();
			ThreadCB.dispatch();
			return FAILURE;
		}

		// If not valid --> continue with page fault handling
		else {

			// Check that the page does not already have a validating thread managing the
			// page fault
			// If so --> suspend and wait for original thread to finish
			if (page.getValidatingThread() != null)
				thread.suspend(page);
			else // Increment page faults
				numPageFaults++;

			System.out.println("Total Number of Page Faults = " + numPageFaults);

			// Checking if there's enough memory
			for (int i = 0; i < MMU.getFrameTableSize(); i++) {
				if (MMU.getFrame(i).isReserved() || MMU.getFrame(i).getLockCount() > 0)
					counter++;
			}

			// Not enough memory --> notify threads suspended on page and dispatch()
			// return NotEnoughMemory
			if (counter == MMU.getFrameTableSize()) {
				page.notifyThreads();
				ThreadCB.dispatch();
				return NotEnoughMemory;
			}

			// Enough memory --> continue page fault handling
			else {
				// Create an event onto which the thread is suspended
				SystemEvent event = new SystemEvent("Page Fault Occurred");
				thread.suspend(event);

				// Set the thread as validating thread for the faulted page
				page.setValidatingThread(thread);

				// Get Free Frame
				FrameTableEntry freeFrame = getFreeFrame();

				// If free frame found
				if (freeFrame != null) {
					// Reserve the frame so no other tasks get control of it before finishing
					// swapping operations
					freeFrame.setReserved(thread.getTask());

					// Update Page Table
					page.setFrame(freeFrame);

					// Swap In Page into Memory
					SwapIn(thread, page);

					// Check Thread Status --> if killed while waiting on swapping, notify threads
					// and return failure
					if (thread.getStatus() == ThreadKill) {
						page.notifyThreads();
						page.setValidatingThread(null);
						event.notifyThreads();
						ThreadCB.dispatch();
						return FAILURE;
					}

					// Update PageTable
					page.setValid(true);

					// Update FrameTable
					freeFrame.setPage(page);
					freeFrame.setReferenced(true);

					// Setting Dirty Flag --> Only if refernce type is MemoryWrite
					if (referenceType == MemoryWrite)
						freeFrame.setDirty(true);

					// Perform Necessary Actions before exiting Page Fault Handler
					// Unreserve, notify, dispatch, return success, set validating thread to null.
					freeFrame.setUnreserved(thread.getTask());
					page.setValidatingThread(null);
					page.notifyThreads();
					event.notifyThreads();
					ThreadCB.dispatch();
					return SUCCESS;
				}

				// If free frame not found
				else {
					// Check User Option for Page Replacement Algorithm (FIFO or ESC)
					if (userOption.equals("Fifo")) {
						frame = Fifo();
					}

					else {
						frame = SecondChance();

					}

					// Reserve the frame so no other tasks get control of it before finishing
					// swapping operations
					frame.setReserved(thread.getTask());

					// Check if dirty --> if so, it needs swapping out.
					if (frame.isDirty()) {
						System.out.println("Dirty Frame");

						// Save previous page of frame before swapping out to later update it's Page
						// Table
						PageTableEntry prevPage = frame.getPage();

						// Swap Out page from Memory
						SwapOut(thread, frame);

						// Check Thread Status --> if killed while waiting on swapping, notify threads
						// and return failure
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #1");
							page.notifyThreads();
							page.setValidatingThread(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}

						// Freeing Frame
						frame.setReferenced(false);

						// Updating Frame's Previous Page
						prevPage.setValid(false);
						prevPage.setFrame(null);

						// Freeing Frame cont.
						frame.setDirty(false);
						frame.setPage(null);

						// Setting frame for current page
						page.setFrame(frame);

						// Swap In Page into Memory
						SwapIn(thread, page);

						// Check Thread Status --> if killed while waiting on swapping, notify threads
						// and return failure
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #2");
							page.notifyThreads();
							page.setValidatingThread(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}

						// Update PageTable
						page.setValid(true);

						// Update FrameTable
						frame.setPage(page);
						frame.setReferenced(true);

						// Setting Dirty Flag --> Only if reference type is MemoryWrite
						if (referenceType == MemoryWrite)
							frame.setDirty(true);

						frame.setUnreserved(thread.getTask());
						page.setValidatingThread(null);
						page.notifyThreads();
						event.notifyThreads();
						ThreadCB.dispatch();
						return SUCCESS;

					}

					// Not Dirty --> No need for swapping out
					else {
						System.out.println("Not Dirty Frame");
						// Setting frame for page
						page.setFrame(frame);

						// Swap In Page into Memory
						SwapIn(thread, page);

						// Check Thread Status (FAILURE if killed)
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #3");
							page.notifyThreads();
							page.setValidatingThread(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}

						// Update PageTable
						page.setValid(true);

						// Update FrameTable
						frame.setPage(page);
						frame.setReferenced(true);

						// Setting Dirty Flag --> Only if reference type is MemoryWrite
						if (referenceType == MemoryWrite)
							frame.setDirty(true);

						// Perform Necessary Actions before exiting Page Fault Handler
						// Unreserve, notify, dispatch, return success, set validating thread to null.
						frame.setUnreserved(thread.getTask());
						page.setValidatingThread(null);
						page.notifyThreads();
						event.notifyThreads();
						ThreadCB.dispatch();
						return SUCCESS;
					}
				}
			}
		}
	}

	/*
	 * Returns the current number of free frames. It does not matter where the
	 * search in the frame table starts, but this method must not change the value
	 * of the reference bits, dirty bits or MMU.Cursor.
	 */

	// Authors: ID:
	// Orjwan Zaafarani 1506807
	// Last Modification Date: 10/4/2020
	public static int numFreeFrames() {
		int freeFrames = 0;
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			FrameTableEntry frame = MMU.getFrame(i);
			if (frame.getPage() == null && !frame.isReserved() && frame.getLockCount() <= 0 && !frame.isReferenced()
					&& !frame.isDirty()) {
				freeFrames++;
			}
		}
		return freeFrames;
	}

	/*
	 * Returns the first free frame starting the search from frame[0].
	 */

	// Authors: ID:
	// Orjwan Zaafarani 1506807
	// Last Modification Date: 10/4/2020
	public static FrameTableEntry getFreeFrame() {
		// Iterates FrameTable for free frames (null page, not reserved, not locked)
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			FrameTableEntry frame = MMU.getFrame(i);
			if (frame.getPage() == null && !frame.isReserved() && frame.getLockCount() == 0) {
				// If found, return the frame
				return frame;
			}
		}
		// If none, return null
		return null;
	}

	/*
	 * Frees frames using the following Second Chance approach and returns one
	 * frame. The search uses the MMU variable MMU.Cursor to specify the starting
	 * frame index of the search. Freeing frames: To free a frame, one should
	 * indicate that the frame does not hold any page (i.e., it holds the null page)
	 * using the setPage() method. The dirty and the reference bits should be set to
	 * false. Updating a page table: To indicate that a page P is no longer valid,
	 * one must set its frame to null (using the setFrame() method) and the validity
	 * bit to false (using the setValid() method). To indicate that the page P has
	 * become valid and is now occupying a main memory frame F, you do the
	 * following: Ã¢â‚¬â€œ use setFrame() to set the frame of P to F Ã¢â‚¬â€œ use
	 * setPage() to set F Ã¢â‚¬â„¢ï¸�s page to P Ã¢â‚¬â€œ set the PÃ¢â‚¬â„¢ï¸�s
	 * validity flag correctly Ã¢â‚¬â€œ set the dirty and reference flags in F
	 * appropriately.
	 * 
	 */

	// Authors: ID:
	// Orjwan Zaafarani 1506807
	// Last Modification Date: 12/4/2020
	// Second Change Page Replacement Algorithm
	public static FrameTableEntry SecondChance() {
		boolean foundFirstDirtyFrame = false;
		int firstDirtyFrameID;
		FrameTableEntry firstDirtyFrame = null;

		// Outer Loop: iterates Frame Table at most twice
		for (int j = 0; j < 2; j++) {

			// Inner Loop: Iterates through all elements of Frame Table once
			for (int i = 0; i < MMU.getFrameTableSize(); i++) {
				// For not freeing more frames than wantFree --> if equal, stop second chance
				if (numFreeFrames() == MMU.wantFree)
					break;

				// Check Reference Bits --> If referenced, set to false
				else if (MMU.getFrame(MMU.Cursor).isReferenced() == true) {
					MMU.getFrame(MMU.Cursor).setReferenced(false);
				}

				// Check if frame is clean (clean, unreserved, not locked, and not referenced)
				else if (MMU.getFrame(MMU.Cursor).getPage() != null & MMU.getFrame(MMU.Cursor).isReferenced() == false
						& MMU.getFrame(MMU.Cursor).isDirty() == false & MMU.getFrame(MMU.Cursor).isReserved() == false
						& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {

					// If so --> set dirty and reference bits to false
					MMU.getFrame(MMU.Cursor).setDirty(false);
					MMU.getFrame(MMU.Cursor).setReferenced(false);

					// + Update the frame's page attributes (make page invalid and nullify frame)
					MMU.getFrame(MMU.Cursor).getPage().setValid(false);
					MMU.getFrame(MMU.Cursor).getPage().setFrame(null);

					// Nullify page for chosen frame
					MMU.getFrame(MMU.Cursor).setPage(null);
				}

				// To Keep track of first dirty page found
				if (foundFirstDirtyFrame == false & MMU.getFrame(MMU.Cursor).isDirty() == true
						& MMU.getFrame(MMU.Cursor).isReserved() == false
						& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
					firstDirtyFrameID = MMU.getFrame(MMU.Cursor).getID();
					firstDirtyFrame = MMU.getFrame(MMU.Cursor);
					foundFirstDirtyFrame = true;
				}
				// Increment MMU.Curson using modulus arithmetic
				MMU.Cursor = (MMU.Cursor + 1) % MMU.getFrameTableSize();
			}

			// For not freeing more frames than wantFree --> if equal, stop second chance
			if (numFreeFrames() == MMU.wantFree)
				break;
		}

		// Return either a freed frame or the dirty frame (in case the algorithm could
		// not free any frames)
		if (numFreeFrames() < MMU.wantFree && foundFirstDirtyFrame == true)
			return firstDirtyFrame;
		else {
			return getFreeFrame();
		}
	}

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 15/4/2020
	// FIFO Page Replacement Algorithm
	public static FrameTableEntry Fifo() {
		long max = 0;
		FrameTableEntry frame = null;

		// Iterating Frame Table to find oldest page for replacement --> Done by
		// Comparing creation time of page to surrent system time
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			// Frame can't be locked or reserved
			if (!MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0) {
				PageTableEntry page = MMU.getFrame(i).getPage();
				long time = System.currentTimeMillis() - page.createTime;
				System.out.println("Time in Frame Table = " + time);
				if (time > max) {
					max = time;
					frame = MMU.getFrame(i);
				}
			}
		}

		System.out.println("FIFO is invoked. Oldest = " + max);

		// If not dirty
		if (!frame.isDirty()) {
			// Freeing the Frame
			frame.setReferenced(false);

			// Updating the victim's Page Table
			frame.getPage().setValid(false);
			frame.getPage().setFrame(null);

			// Freeing the Frame cont.
			frame.setPage(null);
			return getFreeFrame();
		}

		// If dirty
		else
			return frame;
	}

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 17/4/2020
	// Swap Out Method from Memory
	public static void SwapOut(ThreadCB thread, FrameTableEntry frame) {
		System.out.println("Entered Swap Out");
		// Get Swap File to Write
		frame.getPage().getTask().getSwapFile().write(frame.getPage().getID(), frame.getPage(), thread);
		System.out.println("Exited Swap Out");
	}

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 17/4/2020
	// Swap In Method into Memory
	public static void SwapIn(ThreadCB thread, PageTableEntry page) {
		System.out.println("Entered Swap In");
		// Get Swap File to Read
		page.getTask().getSwapFile().read(page.getID(), page, thread);
		System.out.println("Exited Swap In");
	}
}
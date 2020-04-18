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
	static String userOption = "Fifo";
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
	// NOURA: VERYY LONGG CODEEE because there are lots of conditions --> need to
	// make it more efficient and organized.
	// For now, it does what it's supposed to hopefully (not 100% sure) i'll check
	// with GitHub solutions

	public static int do_handlePageFault(ThreadCB thread, int referenceType, PageTableEntry page) {
		int counter = 0;
		FrameTableEntry frame;

		if (page.isValid()) {
			page.notifyThreads();
			ThreadCB.dispatch();
			return FAILURE;
		}

		else {
			if (page.getValidatingThread() != null)
				thread.suspend(page);
			else
				numPageFaults++;
			
			System.out.println("Total Number of Page Faults = " + numPageFaults);
			// Checking if there's enough memory
			for (int i = 0; i < MMU.getFrameTableSize(); i++) {
				if (MMU.getFrame(i).isReserved() || MMU.getFrame(i).getLockCount() > 0)
					counter++;
			}

			// Not enough memory
			if (counter == MMU.getFrameTableSize()) {
				page.notifyThreads();
				ThreadCB.dispatch();
				return NotEnoughMemory;
			}

			// Enough memory
			else {
				SystemEvent event = new SystemEvent("Page Fault Occurred");
				thread.suspend(event);
				page.setValidatingThread(thread);

				FrameTableEntry freeFrame = getFreeFrame();

				// If frame is free
				if (freeFrame != null) {
					System.out.println("Reserving #1");
					freeFrame.setReserved(thread.getTask());
					page.setFrame(freeFrame);

					// Swap In
					SwapIn(thread, page);

					// Check Thread Status
					if (thread.getStatus() == ThreadKill) {
						page.notifyThreads();
						page.setValidatingThread(null);
						page.setFrame(null);
						event.notifyThreads();
						ThreadCB.dispatch();
						return FAILURE;
					}

					// Update PageTable
					page.setValid(true);
					freeFrame.setPage(page);
					freeFrame.setReferenced(true);

					// Update FrameTable

					// Setting Dirty Flag
					if (referenceType == MemoryWrite)
						freeFrame.setDirty(true);

					System.out.println("Unreserving #1");
					freeFrame.setUnreserved(thread.getTask());
					page.setValidatingThread(null);
					page.notifyThreads();
					event.notifyThreads();
					ThreadCB.dispatch();
					return SUCCESS;
				}

				// If not free
				else {
					if (userOption.equals("Fifo")) {
						frame = Fifo();
						System.out.println("Reserving #2");
					} else {
						frame = SecondChance();
						System.out.println("Reserving #3");

					}

					frame.setReserved(thread.getTask());
					
					if (frame.isDirty()) {
						System.out.println("Dirty Frame");
						PageTableEntry prevPage = frame.getPage();
						System.out.println("1");
						
						// Swap Out
						SwapOut(thread, frame);
						System.out.println("2");
						
						// Check Thread Status (FAILURE if killed)
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #1");
							page.notifyThreads();
							page.setValidatingThread(null);
							page.setFrame(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}

						// Freeing Frame
						frame.setReferenced(false);
						prevPage.setValid(false);
						prevPage.setFrame(null);
						frame.setDirty(false);
						frame.setPage(null);

						// Setting frame for page
						page.setFrame(frame);
						System.out.println("3");
						// Swap In
						SwapIn(thread, page);

						// Check Thread Status (FAILURE if killed)
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #2");
							page.notifyThreads();
							page.setValidatingThread(null);
							page.setFrame(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}
						System.out.println("4");
						// Update PageTable
						page.setValid(true);
						frame.setPage(page);
						frame.setReferenced(true);

						// Update FrameTable

						// Setting Dirty Flag
						if (referenceType == MemoryWrite)
							frame.setDirty(true);
						System.out.println("5");
						System.out.println("Unreserving #2");
						frame.setUnreserved(thread.getTask());
						page.setValidatingThread(null);
						page.notifyThreads();
						event.notifyThreads();
						ThreadCB.dispatch();
						return SUCCESS;

					}

					// Not Dirty
					else {
						System.out.println("Not Dirty Frame");
						// Setting frame for page
						page.setFrame(frame);

						// Swap In
						SwapIn(thread, page);

						// Check Thread Status (FAILURE if killed)
						if (thread.getStatus() == ThreadKill) {
							System.out.println("Thread Killed #3");
							page.notifyThreads();
							page.setValidatingThread(null);
							page.setFrame(null);
							event.notifyThreads();
							ThreadCB.dispatch();
							return FAILURE;
						}

						// Update PageTable
						page.setValid(true);
						frame.setPage(page);
						frame.setReferenced(true);

						// Update FrameTable

						// Setting Dirty Flag
						if (referenceType == MemoryWrite)
							frame.setDirty(true);

						System.out.println("Unreserving #3");
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

	// NOURA: modified this method to check pages
	public static int numFreeFrames() {
		int freeFrames = 0;
		// less or less or equal than?
		// will it ever break the loop?
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
	// not sure if this is the right way to check free frames. Do we check the
	// pages?!!!
	// NOURA: modified this method to check pages

	public static FrameTableEntry getFreeFrame() {
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			FrameTableEntry frame = MMU.getFrame(i);
			if (frame.getPage() == null && !frame.isReserved() && frame.getLockCount() == 0) {
				return frame;
			}
		}
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
	public static FrameTableEntry SecondChance() {
		boolean foundFirstDirtyFrame = false;
		int firstDirtyFrameID;
		FrameTableEntry firstDirtyFrame = null;
		// Phase1 - Note5 - Keep in mind that locked and reserved page frames
		// cannot be selected and dirty frames should not be
		// freed in this phase.
		// Phase1 - Note2,3
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < MMU.getFrameTableSize(); i++) {
				// Phase1 - Note1
				if (numFreeFrames() == MMU.wantFree)
					break;

				// Phase1 - Task1
				else if (MMU.getFrame(MMU.Cursor).isReferenced() == true) {
					MMU.getFrame(MMU.Cursor).setReferenced(false);
				}
				// Phase1 - Task2
				else if (MMU.getFrame(MMU.Cursor).getPage() != null & MMU.getFrame(MMU.Cursor).isReferenced() == false
						& MMU.getFrame(MMU.Cursor).isDirty() == false & MMU.getFrame(MMU.Cursor).isReserved() == false
						& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
					// Phase1 - Task2 - a - freeing frames
					MMU.getFrame(MMU.Cursor).setDirty(false);
					MMU.getFrame(MMU.Cursor).setReferenced(false);

					// Phase1 - Task2 - b
					MMU.getFrame(MMU.Cursor).getPage().setValid(false);
					MMU.getFrame(MMU.Cursor).getPage().setFrame(null);

					// Phase1 - Task2 - a - freeing frames
					MMU.getFrame(MMU.Cursor).setPage(null);

				}

				// Phase1 - Task3
				if (foundFirstDirtyFrame == false & MMU.getFrame(MMU.Cursor).isDirty() == true
						& MMU.getFrame(MMU.Cursor).isReserved() == false
						& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
					firstDirtyFrameID = MMU.getFrame(MMU.Cursor).getID();
					firstDirtyFrame = MMU.getFrame(MMU.Cursor);
					foundFirstDirtyFrame = true;
				}
				// Phase1 - Note4
				MMU.Cursor = (MMU.Cursor + 1) % MMU.getFrameTableSize();
			}

			if (numFreeFrames() == MMU.wantFree)
				break;
		}

		// Phase2 and Phase3
		if (numFreeFrames() < MMU.wantFree && foundFirstDirtyFrame == true)
			return firstDirtyFrame;
		else {
			return getFreeFrame();
		}
	}

	public static FrameTableEntry Fifo() {
//		for(int i=0;i<MMU.getFrameTableSize();i++) {
//			MMU.getFrame(i).setReferenced(true);
//		}
//		return SecondChance();

//		FrameTableEntry foundFrame = null;
//		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
//
//			if(numFreeFrames() == MMU.wantFree)
//				break;
//
//			// get first clean page
//			else if (MMU.getFrame(MMU.Cursor).getPage() != null
//					& MMU.getFrame(MMU.Cursor).isReserved() == false
//					& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
//				// free frame
//				MMU.getFrame(MMU.Cursor).getPage().setValid(false);
//				MMU.getFrame(MMU.Cursor).getPage().setFrame(null);
//				MMU.getFrame(MMU.Cursor).setPage(null);
//				foundFrame = MMU.getFrame(MMU.Cursor);;
//			}
//			// update frame's cursor
//			MMU.Cursor=(MMU.Cursor+1)%MMU.getFrameTableSize();
//		}
////		if(numFreeFrames() == MMU.wantFree)
////			break;
//			if(numFreeFrames() < MMU.wantFree)	
//			return getFreeFrame();
//		
//		return foundFrame;

//		NOURA
		long max = 0;
		FrameTableEntry frame = null;
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
			if (!MMU.getFrame(i).isReserved() && MMU.getFrame(i).getLockCount() == 0) {
				PageTableEntry page = MMU.getFrame(i).getPage();
				if (page != null)
					System.out.println("Page not null.");
				else
					System.out.println("Page null.");
				long time = System.currentTimeMillis() - page.createTime;
				System.out.println("Time in Frame Table = " + time);
				if (time > max) {
					max = time;
					frame = MMU.getFrame(i);
				}
			}
		}

		System.out.println("FIFO is invoked. Oldest = " + max);

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

		else
			return frame;

//		FrameTableEntry frameMax = null;
//		long maxTillNow=-1;
//		for(int i = 0; i < MMU.getFrameTableSize(); i++)
//    	{
//			FrameTableEntry frame = MMU.getFrame(i);
//			PageTableEntry page = frame.getPage();
//			if(Math.abs(HClock.get() - page.reftimer) > maxTillNow){
//				frameMax = frame;
//				maxTillNow= Math.abs(HClock.get() - page.reftimer);
//			}	
//    	}
//		return frameMax;
	}

	public static void SwapOut(ThreadCB thread, FrameTableEntry frame) {
		System.out.println("Entered Swap Out");
		frame.getPage().getTask().getSwapFile().write(frame.getPage().getID(),frame.getPage(), thread);
		System.out.println("Exited Swap Out");
	}

	public static void SwapIn(ThreadCB thread, PageTableEntry page) {
		page.getTask().getSwapFile().read(page.getID(), page, thread);
	}

}
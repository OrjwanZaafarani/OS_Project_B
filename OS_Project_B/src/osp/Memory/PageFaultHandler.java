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

/**
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler
{
    
    
    
    /**
        This method handles a page fault.

        It must check and return if the page is valid,

        It must check if the page is already being brought in by some other
		thread, i.e., if the page has already pagefaulted
		(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.

        If none of the above is true, a new frame must be chosen
        and reserved until the swap in of the requested
        page into this frame is complete.

		Note that you have to make sure that the validating thread of
		a page is set correctly. To this end, you must set the page's
		validating thread using setValidatingThread() when a pagefault
		happens and you must set it back to null when the pagefault is over.

		If no free frame could be found, then a page replacement algorithm
		must be used to select a victim page to be replaced.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissasociated
        from the frame and marked invalid. After the swap-in, the
        frame must be marked clean. The swap-ins and swap-outs
        must be preformed using regular calls to read() and write().

        The student implementation should define additional methods, e.g,
        a method to search for an available frame, and a method to select
        a victim page making its frame available.

		Note: multiple threads might be waiting for completion of the
		page fault. The thread that initiated the pagefault would be
		waiting on the IORBs that are tasked to bring the page in (and
		to free the frame during the swapout). However, while
		pagefault is in progress, other threads might request the same
		page. Those threads won't cause another pagefault, of course,
		but they would enqueue themselves on the page (a page is also
		an Event!), waiting for the completion of the original
		pagefault. It is thus important to call notifyThreads() on the
		page at the end -- regardless of whether the pagefault
		succeeded in bringing the page in or not.

        @param thread		 the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page 		 the memory page

		@return SUCCESS 	 is everything is fine; FAILURE if the thread
		dies while waiting for swap in or swap out or if the page is
		already in memory and no page fault was necessary (well, this
		shouldn't happen, but...). In addition, if there is no frame
		that can be allocated to satisfy the page fault, then it
		should return NotEnoughMemory

        @OSPProject Memory
    */
	//NOURA: VERYY LONGG CODEEE because there are lots of conditions --> need to make it more efficient and organized.
	//For now, it does what it's supposed to hopefully (not 100% sure) i'll check with GitHub solutions
	public static int do_handlePageFault(ThreadCB thread,int referenceType,PageTableEntry page) {
    	int counter = 0;
    	if(!page.isValid() && page.getValidatingThread() == null) {
	    	for(int i = 0; i < MMU.getFrameTableSize(); i++) {
	    		if (MMU.getFrame(i).isReserved() 
	    			|| MMU.getFrame(i).getLockCount() != 0)
	    			counter++;
	    	}
	    	if(counter == MMU.getFrameTableSize()) {
	        	page.notifyThreads();
	    		ThreadCB.dispatch();
	    		return NotEnoughMemory; 
	    	}
	    	else {
	    		page.setValidatingThread(thread);
	    		SystemEvent SE = new SystemEvent("Suspended Thread");
	    		thread.suspend(SE);
	    		FrameTableEntry freeFrame = getFreeFrame();
	    		OpenFile SwapFile = page.getTask().getSwapFile();
	    		if(freeFrame == null) {
	    			FrameTableEntry SCframe = SecondChance(); 
	    			SCframe.setReserved(thread.getTask());
	    			if(SCframe.isDirty()) {
	    				//SwapOut
	    				SwapFile.write(page.getID(), page, thread);
	    				if(thread.getStatus()==ThreadKill)
	    	    			return FAILURE;
	    				//Freeing the frame
	    				SCframe.setPage(null);
	    				SCframe.setDirty(false);
	    				SCframe.setReferenced(false);
	    			}
	    			//SwapIn
	    			page.setFrame(SCframe);
	    			SwapFile.read(page.getID(), page, thread);
	    			if(thread.getStatus()==ThreadKill)
		    			return FAILURE;
	    	    	SCframe.setPage(page);
	    	    	SCframe.setUnreserved(thread.getTask());
	    	    	page.setValid(true);
	    	    	if(referenceType == MemoryWrite)
		    			SCframe.setDirty(true);
		    		else
		    			SCframe.setDirty(false);
	    	    	SE.notifyThreads();
	    	    	page.setValidatingThread(null);
	    	    	page.notifyThreads();
	    	    	ThreadCB.dispatch();
	    	    	return SUCCESS;
	    		}
	    		
	    		freeFrame.setReserved(thread.getTask());
	    		page.setFrame(freeFrame);
	    		//Swap In
	    		SwapFile.read(page.getID(), page, thread);
	    		if(thread.getStatus()==ThreadKill)
	    			return FAILURE;
	    		freeFrame.setPage(page);
	    		page.setValid(true);
	    		freeFrame.setReferenced(true);
	    		if(referenceType == MemoryWrite)
	    			freeFrame.setDirty(true);
	    		else
	    			freeFrame.setDirty(false);
	    		freeFrame.setUnreserved(thread.getTask());
	    		SE.notifyThreads();
	    		page.setValidatingThread(null);
	    		page.notifyThreads();
	    		ThreadCB.dispatch();
	    		return SUCCESS;
	    	}
    	}
    	
    	page.notifyThreads();
    	ThreadCB.dispatch();
    	return FAILURE;
    }

    /*
     * Returns the current number of free frames. It does not matter where 
     * the search in the frame table starts, but this method must not change 
     * the value of the reference bits, dirty bits or MMU.Cursor.
     */
    
    //NOURA: modified this method to check pages
    public static int numFreeFrames() {
    	int freeFrames = 0;
    	// less or less or equal than?
    	// will it ever break the loop?
    	for (int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
	    	if(frame.getPage() == null 
	    		&& !frame.isReserved() 
	    		&& frame.getLockCount() == 0) {
	    		freeFrames++;
	    	}
    	}
    	return freeFrames;
    }
    
    /*
     * Returns the first free frame starting the search from frame[0].
     */
    // not sure if this is the right way to check free frames. Do we check the pages?!!!
    //NOURA: modified this method to check pages
	public static FrameTableEntry getFreeFrame() {
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {
    		FrameTableEntry frame = MMU.getFrame(i);
	    	if(frame.getPage() == null 
	    		&& !frame.isReserved() 
	    		&& frame.getLockCount() == 0) {
	    			return frame;
	    		}
	    	}
	    	return null;
	}
	
	/*
	 * Frees frames using the following Second Chance approach and 
	 * returns one frame. The search uses the MMU variable MMU.Cursor 
	 * to specify the starting frame index of the search.
	 * Freeing frames: To free a frame, one should indicate that the frame
	 * does not hold any page (i.e., it holds the null page) using the
	 * setPage() method. The dirty and the reference bits should be set to false.
	 * Updating a page table: To indicate that a page P is no longer valid, one
		must set its frame to null (using the setFrame() method) and the validity
		bit to false (using the setValid() method). To indicate that the page P
		has become valid and is now occupying a main memory frame F, you do the
		following:
			– use setFrame() to set the frame of P to F
			– use setPage() to set F ’s page to P
			– set the P’s validity flag correctly
			– set the dirty and reference flags in F appropriately.

	 */
	public static FrameTableEntry SecondChance() {
		boolean foundFirstDirtyFrame = false;
		int firstDirtyFrameID;
		FrameTableEntry firstDirtyFrame = null;
		// Phase1 - Note5 - Keep  in  mind  that  locked  and reserved  page  frames
		// 					cannot  be  selected  and dirty  frames should not be
		//					freed in this phase.
		// Phase1 - Note2,3
		for(int j = 0; j < 2; j++) { 
			for(int i = 0; i < MMU.getFrameTableSize(); i++) {
				// Phase1 - Note1
				if(numFreeFrames() >= MMU.wantFree)
					break;
				
				// Phase1 - Task1
				else if(MMU.getFrame(MMU.Cursor).isReferenced() == true) {
					MMU.getFrame(MMU.Cursor).setReferenced(false);
				}
				// Phase1 - Task2
				else if (MMU.getFrame(MMU.Cursor).getPage()!=null
						& MMU.getFrame(MMU.Cursor).isReferenced()==false
						& MMU.getFrame(MMU.Cursor).isDirty()==false
						& MMU.getFrame(MMU.Cursor).isReserved()==false
						& MMU.getFrame(MMU.Cursor).getLockCount()<=0) {
					// Phase1 - Task2 - a - freeing frames
					MMU.getFrame(MMU.Cursor).setPage(null);
					MMU.getFrame(MMU.Cursor).setDirty(false);
					MMU.getFrame(MMU.Cursor).setReferenced(false);
					// The dirty and the reference bits should be set to false. is this done?
					// Phase1 - Task2 - b
					MMU.getFrame(MMU.Cursor).getPage().setFrame(null);
					MMU.getFrame(MMU.Cursor).getPage().setValid(false);
				}
				// Phase1 - Task3
				if (foundFirstDirtyFrame == false
					& MMU.getFrame(MMU.Cursor).isDirty() == true
					& MMU.getFrame(MMU.Cursor).isReserved() == false
					& MMU.getFrame(MMU.Cursor).getLockCount() <= 0) {
					firstDirtyFrameID = MMU.getFrame(MMU.Cursor).getID();
					firstDirtyFrame = MMU.getFrame(MMU.Cursor);
					foundFirstDirtyFrame = true; 
				}
				// Phase1 - Note4
				MMU.Cursor=(MMU.Cursor+1)%MMU.getFrameTableSize();
			}
			
			if(numFreeFrames() >= MMU.wantFree)
				break;
		}
		
		// Phase2 and Phase3
		if(numFreeFrames() < MMU.wantFree && foundFirstDirtyFrame == true) 
			return firstDirtyFrame;	
		else 
			return getFreeFrame();
	}
}
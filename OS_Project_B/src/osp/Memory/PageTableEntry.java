package osp.Memory;

import osp.Hardware.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Devices.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
 * The PageTableEntry object contains information about a specific virtual page
 * in memory, including the page frame in which it resides.
 * 
 * @OSPProject Memory
 */

public class PageTableEntry extends IflPageTableEntry {
	/**
	 * The constructor. Must call
	 * 
	 * super(ownerPageTable,pageNumber);
	 * 
	 * as its first statement.
	 * 
	 * @OSPProject Memory
	 */
	// Global Variable to be used by FIFO
	long createTime;

	// Page Tale Entry Constructor
	public PageTableEntry(PageTable ownerPageTable, int pageNumber) {
		super(ownerPageTable, pageNumber);

		// Stores pages' creation time
		this.createTime = System.currentTimeMillis();

	}

	/**
	 * This method increases the lock count on the page by one.
	 * 
	 * The method must FIRST increment lockCount, THEN check if the page is valid,
	 * and if it is not and no page validation event is present for the page, start
	 * page fault by calling PageFaultHandler.handlePageFault().
	 * 
	 * @return SUCCESS or FAILURE FAILURE happens when the pagefault due to locking
	 *         fails or the that created the IORB thread gets killed.
	 * 
	 * @OSPProject Memory
	 */

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 4/4/2020
	// Increments lock for requested frame
	public int do_lock(IORB iorb) {

		// Checks if page is valid --> if so, increment lock of corresponding frame and
		// return success
		if (this.isValid()) {
			getFrame().incrementLockCount();
			return SUCCESS;
		}

		// If page invalid
		else {

			// Check that the page does have a validating thread --> If not, call page fault
			// handler
			if (getValidatingThread() == null) {
				// Return FAILURE if page fault handling fails
				if (PageFaultHandler.handlePageFault(iorb.getThread(), MemoryLock, this) == FAILURE)
					return FAILURE;
				// If page fault handling success --> increment lock of corresponding frame and
				// return success
				else {
					getFrame().incrementLockCount();
					return SUCCESS;
				}
			}

			// If page has a validating thread same as the IORB thread --> increment lock of
			// corresponding frame and return success
			else if (getValidatingThread() == iorb.getThread()) {
				getFrame().incrementLockCount();
				return SUCCESS;
			}

			// Has a validating thread not the same as IORB thread
			else {
				// Suspend thread
				iorb.getThread().suspend(this);

				// Check Status of thread after suspension --> return FAILURE if thread is
				// killed
				if (iorb.getThread().getStatus() == ThreadKill)
					return FAILURE;

				// If not killed --> increment lock of corresponding frame and return success
				else {
					getFrame().incrementLockCount();
					return SUCCESS;
				}

			}
		}
	}

	/**
	 * This method decreases the lock count on the page by one.
	 * 
	 * This method must decrement lockCount, but not below zero.
	 * 
	 * @OSPProject Memory
	 */

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 4/4/2020
	public void do_unlock() {
		// Check lock count in order to not decrement below zero
		// If permissible --> decrement lock count of corresponding frame
		if (getFrame().getLockCount() > 0)
			getFrame().decrementLockCount();
	}
}

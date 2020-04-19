package osp.Memory;

/**
    The PageTable class represents the page table for a given task.
    A PageTable consists of an array of PageTableEntry objects.  This
    page table is of the non-inverted type.

    @OSPProject Memory
*/
import java.lang.Math;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Hardware.*;

public class PageTable extends IflPageTable {
	/**
	 * The page table constructor. Must call
	 * 
	 * super(ownerTask)
	 * 
	 * as its first statement. Then it must figure out what should be the size of a
	 * page table, and then create the page table, populating it with items of type,
	 * PageTableEntry.
	 * 
	 * @OSPProject Memory
	 */

	// Page Table Constructor
	public PageTable(TaskCB ownerTask) {
		super(ownerTask);

		// Find size of page table by referring to the Page Address Bits
		int MaxNumberofPages = (int) Math.pow(2, MMU.getPageAddressBits());

		// Creates pages to fill up page table
		pages = new PageTableEntry[MaxNumberofPages];
		for (int i = 0; i < MaxNumberofPages; i++)
			pages[i] = new PageTableEntry(this, i);
	}

	/**
	 * Frees up main memory occupied by the task. Then unreserves the freed pages,
	 * if necessary.
	 * 
	 * @OSPProject Memory
	 */

	// Authors: ID:
	// Noura Al-Dakhil 1614549
	// Last Modification Date: 4/4/2020
	public void do_deallocateMemory() {
		// Iterates Frame Table
		for (int i = 0; i < MMU.getFrameTableSize(); i++) {

			// If Occupied by calling task --> deallocate memory
			if (MMU.getFrame(i).getPage() != null) {
				if (MMU.getFrame(i).getPage().getTask() == this.getTask()) {

					// Frees Frame
					MMU.getFrame(i).setPage(null);
					MMU.getFrame(i).setDirty(false);
					MMU.getFrame(i).setReferenced(false);

					// If reserved by task, unreserve it.
					if (MMU.getFrame(i).getReserved() == this.getTask())
						MMU.getFrame(i).setUnreserved(this.getTask());
				}
			}

		}
	}
}

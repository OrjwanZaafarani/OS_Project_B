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

public class PageTable extends IflPageTable
{
    /**
	   The page table constructor. Must call

	       super(ownerTask)

	   as its first statement. Then it must figure out
	   what should be the size of a page table, and then
	   create the page table, populating it with items of
	   type, PageTableEntry.

	   @OSPProject Memory
    */
    public PageTable(TaskCB ownerTask)
    {
    	super(ownerTask);
    	int MaxNumberofPages = (int) Math.pow(2,MMU.getPageAddressBits());
    	pages = new PageTableEntry[MaxNumberofPages];
    	for(int i = 0; i<MaxNumberofPages; i++)
    		pages[i] = new PageTableEntry(this, i);
    }

    /**
       Frees up main memory occupied by the task.
       Then unreserves the freed pages, if necessary.

       @OSPProject Memory
    */
    public void do_deallocateMemory() {
    	for(int i = 0; i < MMU.getFrameTableSize(); i++){
    		if(MMU.getFrame(i).getPage() != null) {
	    		if (MMU.getFrame(i).getPage().getTask()==this.getTask()) {
	    			MMU.getFrame(i).setPage(null);
	    			MMU.getFrame(i).setDirty(false);
	    			MMU.getFrame(i).setReferenced(false);
	    			
	    			if(MMU.getFrame(i).getReserved()==this.getTask())
	    				MMU.getFrame(i).setUnreserved(this.getTask());
	    		}
    		}
    		
    	}
    		
    }
}

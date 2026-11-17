package pack.operSys.model;

/**
 * Holds the step-by-step MMU translation from a logical address to a physical address.
 */
public class AddressTranslationResult {

    public final boolean valid;
    public final String statusMessage;
    public final String pid;
    public final int logicalAddress;
    public final int pageSizeBytes;
    public final int pageNumber;
    public final int offset;
    public final int frameNumber;
    public final int physicalAddress;
    public final boolean inInternalFrag;

    public AddressTranslationResult(
            boolean valid,
            String statusMessage,
            String pid,
            int logicalAddress,
            int pageSizeBytes,
            int pageNumber,
            int offset,
            int frameNumber,
            int physicalAddress,
            boolean inInternalFrag) {
        this.valid = valid;
        this.statusMessage = statusMessage;
        this.pid = pid;
        this.logicalAddress = logicalAddress;
        this.pageSizeBytes = pageSizeBytes;
        this.pageNumber = pageNumber;
        this.offset = offset;
        this.frameNumber = frameNumber;
        this.physicalAddress = physicalAddress;
        this.inInternalFrag = inInternalFrag;
    }
}
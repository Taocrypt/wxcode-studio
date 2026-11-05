import sys,capstone
from elftools.elf.elffile import ELFFile
f=open(sys.argv[1],'rb'); e=ELFFile(f)
def read(va,n):
    for s in e.iter_segments():
        if s['p_type']=='PT_LOAD' and s['p_vaddr']<=va<s['p_vaddr']+s['p_filesz']:
            f.seek(s['p_offset']+va-s['p_vaddr']); return f.read(n)
md=capstone.Cs(capstone.CS_ARCH_ARM64,capstone.CS_MODE_ARM)
a=int(sys.argv[2],16); b=int(sys.argv[3],16)
for i in md.disasm(read(a,b-a),a): print(hex(i.address),i.bytes.hex(),i.mnemonic,i.op_str)

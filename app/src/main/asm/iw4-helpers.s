.text
.macro call_native address
    lis 12, (\address >> 16)
    ori 12, 12, (\address & 0xffff)
    mtctr 12
    bctrl
.endm
.macro return_native
    addi 1, 1, 0xa0
    ld 28, -40(1)
    ld 29, -32(1)
    ld 30, -24(1)
    ld 31, -16(1)
    lwz 12, -8(1)
    mtlr 12
    blr
.endm
.globl dispatch
dispatch:
    cmplwi 10, 0xf000
    beq custom
    lwzx 6, 8, 7
    b resume
custom:
    lis 6, 0x823c
    ori 6, 6, 0x8040
resume:
    lis 12, 0x8224
    ori 12, 12, 0xe340
    mtctr 12
    bctr
.org 0x40
    mflr 12
    stw 12, -8(1)
    std 28, -40(1)
    std 29, -32(1)
    std 30, -24(1)
    std 31, -16(1)
    stwu 1, -0xa0(1)
    lis 31, 0x835c
    lwz 30, 0x49e8(31)
    lwz 29, 0x49f0(31)
    cmplwi 29, 2
    blt invalid
    lwz 11, 4(30)
    cmpwi 11, 6
    bne invalid
    lwz 28, 0(30)
    cmplwi 28, 4
    bgt invalid
    lwz 11, -4(30)
    cmpwi 11, 6
    bne invalid
    lwz 31, -8(30)
    cmpwi 28, 4
    beq rpc
    lis 11, 0x8300
    cmplw 31, 11
    blt invalid
    lis 11, 0x83dc
    ori 11, 11, 0x8600
    cmplw 31, 11
    bge invalid
    cmpwi 28, 0
    beq read_byte
    cmpwi 28, 1
    beq write_byte
    cmpwi 28, 2
    beq read_string
    b write_string
read_byte:
    cmpwi 29, 2
    bne invalid
    lbz 3, 0(31)
    call_native 0x8224c2c8
    b done
write_byte:
    cmpwi 29, 3
    bne invalid
    lwz 11, -12(30)
    cmpwi 11, 6
    bne invalid
    lwz 11, -16(30)
    stb 11, 0(31)
    b done
read_string:
    cmpwi 29, 2
    bne invalid
    li 10, 0
read_scan:
    add 9, 31, 10
    lis 11, 0x83dc
    ori 11, 11, 0x8600
    cmplw 9, 11
    bge invalid
    lbz 11, 0(9)
    cmpwi 11, 0
    beq read_ready
    addi 10, 10, 1
    cmplwi 10, 1024
    blt read_scan
    b invalid
read_ready:
    mr 3, 31
    call_native 0x8224c620
    b done
write_string:
    cmpwi 29, 3
    bne invalid
    lwz 11, -12(30)
    cmpwi 11, 2
    bne invalid
    li 3, 2
    call_native 0x8224b948
    cmpwi 3, 0
    beq invalid
    mr 30, 3
    li 10, 0
write_scan:
    lbzx 11, 30, 10
    addi 10, 10, 1
    cmplwi 10, 1024
    bgt invalid
    cmpwi 11, 0
    bne write_scan
    add 9, 31, 10
    lis 11, 0x83dc
    ori 11, 11, 0x8600
    cmplw 9, 11
    bgt invalid
    li 9, 0
write_copy:
    lbzx 11, 30, 9
    stbx 11, 31, 9
    addi 9, 9, 1
    cmplw 9, 10
    blt write_copy
    b done
rpc:
    lis 11, 0x8225
    ori 11, 11, 0x48d8
    cmplw 31, 11
    beq server_command
    lis 11, 0x8222
    ori 11, 11, 0x4990
    cmplw 31, 11
    bne invalid
    cmpwi 29, 4
    bne invalid
    lwz 11, -12(30)
    cmpwi 11, 6
    bne invalid
    lwz 28, -16(30)
    cmplwi 28, 3
    bgt invalid
    lwz 11, -20(30)
    cmpwi 11, 2
    bne invalid
    li 3, 3
    call_native 0x8224b948
    mr 4, 3
    mr 3, 28
    call_native 0x82224990
    b done
server_command:
    cmpwi 29, 5
    bne invalid
    lwz 11, -12(30)
    cmpwi 11, 6
    bne invalid
    lwz 28, -16(30)
    cmpwi 28, -1
    blt invalid
    cmpwi 28, 17
    bgt invalid
    lwz 11, -20(30)
    cmpwi 11, 6
    bne invalid
    lwz 29, -24(30)
    cmplwi 29, 1
    bgt invalid
    lwz 11, -28(30)
    cmpwi 11, 2
    bne invalid
    li 3, 4
    call_native 0x8224b948
    mr 5, 3
    mr 4, 29
    mr 3, 28
    call_native 0x822548d8
    b done
invalid:
    lis 11, 0x823c
    lwz 10, 0x7910(11)
    addi 10, 10, 1
    stw 10, 0x7910(11)
    call_native 0x82249d88
done:
    return_native

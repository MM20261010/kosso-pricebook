#!/usr/bin/env python3
"""Deny network syscalls for this process and all executed descendants (Linux test host only)."""
import ctypes, ctypes.util, errno, os, sys
lib=ctypes.CDLL(ctypes.util.find_library('seccomp'),use_errno=True)
lib.seccomp_init.argtypes=[ctypes.c_uint32];lib.seccomp_init.restype=ctypes.c_void_p
lib.seccomp_syscall_resolve_name.argtypes=[ctypes.c_char_p];lib.seccomp_syscall_resolve_name.restype=ctypes.c_int
lib.seccomp_rule_add.argtypes=[ctypes.c_void_p,ctypes.c_uint32,ctypes.c_int,ctypes.c_uint]
lib.seccomp_load.argtypes=[ctypes.c_void_p];lib.seccomp_release.argtypes=[ctypes.c_void_p]
ctx=lib.seccomp_init(0x7fff0000)
if not ctx:raise SystemExit('seccomp init failed')
for call in ('socket','connect','sendto','sendmsg','sendmmsg'):
 number=lib.seccomp_syscall_resolve_name(call.encode())
 if number>=0 and lib.seccomp_rule_add(ctx,0x00050000|errno.EPERM,number,0)!=0:raise SystemExit('seccomp rule failed')
if lib.seccomp_load(ctx)!=0:raise SystemExit('seccomp load failed')
lib.seccomp_release(ctx)
os.execvp(sys.argv[1],sys.argv[1:])

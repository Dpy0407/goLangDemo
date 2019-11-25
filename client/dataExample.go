package main

import (
	. "../blockData"
	"encoding/binary"
	"fmt"
	"math/rand"
	"time"
)

type IDataExample struct {
	IBlockData
}

func (this *IDataExample) InitData() {
	this.Init()
	rand.Seed(time.Now().UnixNano())
	this.BlockToken = rand.Uint32()
	dlen := 2048 + rand.Uint32()%4096

	this.RawData = make([]byte, dlen)
	for i := 0; i < int(dlen-4); i = i + 4 {
		x := rand.Uint32()
		binary.LittleEndian.PutUint32(this.RawData[i:i+4], x)
	}

	binary.LittleEndian.PutUint32(this.RawData[dlen-4:dlen], rand.Uint32())

	this.DataLen = int(dlen)

	fmt.Print("generate random data:\r\n")
	this.dumpData(0)
}

func (this *IDataExample) dumpData(ori int) {
	var s string
	tk := make([]byte, 4)
	if ori == 0 {
		s = ">>>"
	} else {
		s = "<<<"
	}
	binary.LittleEndian.PutUint32(tk, this.BlockToken)
	fmt.Printf("%s  data token: %v\r\n", s, tk)
	fmt.Printf("%s  data len:   %d\r\n", s, this.DataLen)
	dlen := this.DataLen
	if dlen > 32 {
		fmt.Printf("%s  content:    [", s)
		for i := 0; i < 8; i++ {
			fmt.Printf("0x%02X ", this.RawData[i])
		}
		fmt.Printf("... ")
		for i := dlen - 7; i < dlen; i++ {
			fmt.Printf("0x%02X ", this.RawData[i])
		}
		fmt.Printf("0x%02X", this.RawData[dlen-1])
		fmt.Printf("]\r\n")
	}
}

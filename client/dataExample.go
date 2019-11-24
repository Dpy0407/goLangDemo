package main

import (
	. "../blockData"
	"encoding/binary"
	"math/rand"
	"time"
)

const DATA_LEN = 4096

type IDataExample struct {
	IBlockData
}

func (this *IDataExample) InitData() {
	this.Init()
	rand.Seed(time.Now().UnixNano())
	this.BlockToken = rand.Uint32()
	this.RawData = make([]byte, DATA_LEN)
	for i := 0; i < DATA_LEN-4; i = i + 4 {
		x := rand.Uint32()
		binary.LittleEndian.PutUint32(this.RawData[i:i+4], x)
	}
	this.DataLen = DATA_LEN
}

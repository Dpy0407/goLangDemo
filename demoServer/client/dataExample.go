package main

import (
	. "../blockData"
	"encoding/binary"
	"fmt"
	"log"
	"math/rand"
	"os"
	"time"
)

type IDataExample struct {
	IBlockData
}

func (this *IDataExample) LoadData(path string) bool {
	info, err := os.Stat(path)
	if err != nil {
		log.Printf("get file info error, file: %s", path)
		return false
	}
	dataSize := info.Size()
	fileObj, err := os.Open(path)
	defer fileObj.Close()

	if err != nil {
		log.Printf("open file failed, file: %s", path)
		return false
	}

	data := make([]byte, dataSize)
	_, err = fileObj.Read(data)
	if err != nil {
		log.Printf("read file failed, file: %s", path)
		return false
	}

	this.Init()
	this.SendStep = DATA_BASE_SEND_STEP
	rand.Seed(time.Now().UnixNano())
	this.BlockToken = rand.Uint32()

	this.BlockToken |= 0xF
	this.RawData = data
	this.DataLen = len(data)

	return true
}

func (this *IDataExample) InitData() {
	this.Init()
	this.SendStep = DATA_BASE_SEND_STEP
	rand.Seed(time.Now().UnixNano())
	this.BlockToken = rand.Uint32()
	// used to debug
	this.BlockToken &= 0xFFFFFFF0

	dlen := 2048 + rand.Uint32()%4096

	this.RawData = make([]byte, dlen)
	for i := 0; i < int(dlen-4); i = i + 4 {
		x := rand.Uint32()
		binary.LittleEndian.PutUint32(this.RawData[i:i+4], x)
	}

	binary.LittleEndian.PutUint32(this.RawData[dlen-4:dlen], rand.Uint32())

	this.DataLen = int(dlen)

	log.Print("generate random data:\r\n")
	this.dumpData(0)
}

func (this *IDataExample) dumpData(ori int) {
	var s string
	if ori == 0 {
		s = ">>>"
	} else {
		s = "<<<"
	}
	fmt.Printf("%s  data token: 0x%08X\r\n", s, this.BlockToken)
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

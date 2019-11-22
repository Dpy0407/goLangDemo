package main

const (
	DATA_STATE_EMPTY = iota
	DATA_STATE_RICIEVING
	DATA_STATE_SEND_REQUIRED
	DATA_STATE_DONE
)

type IBlockData struct {
	rawData    []byte
	blockToken uint32
	lastSeq    uint8
	state      int
}

#!/usr/bin/python

import socket
import sys
import time
import urllib2
from urllib2 import urlopen,HTTPError

TIMEOUT = 5  # seconds
NORMAL_FILES = ['/index.html', '/foo/bar.html']
REDIRECTS = [['/cats', 'http://en.wikipedia.org/wiki/Cat'],
						 ['/uchicago/cs', 'http://www.cs.uchicago.edu/']]
NOTFOUNDS = ['/redirect.defs', '/not/a/real/url.html']

# prevents us from following redirects, so we can get the HTTP response code
class DontFollowHttpRedirectHandler(urllib2.HTTPRedirectHandler):
	def http_error_307(self, req, fp, code, msg, headers):
		raise urllib2.HTTPError(req.get_full_url(), code, msg, headers, fp)
	http_error_302 = http_error_303 = http_error_307 #= http_error_301


class NonPersistentTestRunner:
	def __init__(self, host, port, scheme='http'):
		self._host = host
		self._port = port
		self._scheme = scheme
  
	def _build_url(self, filename):
		return '%s://%s:%s%s' % (self._scheme, self._host, self._port, filename)

	# returns request, response, code
	# response is null for 4xx or 5xx response
	def _maybe_fetch(self, filename, op='GET'):
		request = urllib2.Request(self._build_url(filename))
		request.get_method = lambda : op
		opener = urllib2.build_opener(DontFollowHttpRedirectHandler)
		response = None
		try:
			response = opener.open(request)
		except HTTPError as e:
			return request, None, e.code
		return request, response, response.code

	def test_POST(self):
		print 'test POST (unsupported)'
		for filename in NORMAL_FILES:
			request, response, code = self._maybe_fetch(filename, op='POST')
			if code != 403:
				return False
		return True

	def test_INVALID(self):
		print 'test INVALID'
		for filename in NORMAL_FILES:
			request, response, code = self._maybe_fetch(filename, op='FOOBAR')
			if code != 403 and code < 500:
				return False
		return True

	def test_200(self, opcode='GET'):
		print 'test 200s ' + opcode
		for filename in NORMAL_FILES:
			print '\t%s' % (filename)
			request, response, code = self._maybe_fetch(filename, op=opcode)
			if code != 200 or response is None:
				return False
			if opcode=='HEAD' and len(response.readlines()) != 0:
				return False
		return True

	def test_404(self):
		print 'test 404s'
		for filename in NOTFOUNDS:
			print '\t%s' % (filename)
			request, response, code = self._maybe_fetch(filename)
			if code != 404 or response != None:
				return False
		return True

	def test_301(self, opcode='GET'):
		print 'test 301s ' + opcode
		for filename, redirect in REDIRECTS:
			print '\t%s--->%s' % (filename, redirect)
			request, response, code = self._maybe_fetch(filename, op=opcode)
			# because we followed the redirect, the code should actually be 200,
			# and there should be a response.
			if code != 200 or response == None:
				return False
			if response.url != redirect:
				return False
		return True

	def run_all_tests(self):
		print self.test_200()
		print self.test_404()
		print self.test_301()
		print self.test_200(opcode='HEAD')
		print self.test_301(opcode='HEAD')
		print self.test_POST()
		print self.test_INVALID()		


class PersistentTestRunner:
	def __init__(self, host, port):
		self._host = host
		self._port = port

	def test_200(self):
		try:
			sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		except socket.error, msg:
			print 'error creating a TCP socket!', msg[1]

		try:
			sock.connect((self._host, int(self._port)))
			sock.settimeout(1.0)
		except socket.error, msg:
			print 'error connecting to %s:%s' % (self._host, self._port)

		for filename in NORMAL_FILES:
			response = ''
			try:
				url = 'http://%s:%s%s' % (self._host, self._port, filename)
				sock.send("GET %s HTTP/1.1\r\nConnection: Keep-Alive\r\nHost: %s:%s\r\n\r\n" % (filename, self._host, self._port))
				data = sock.recv(1024)
				while len(data):
					response = response + data
					data = sock.recv(1024)
			except socket.timeout as e:
			  print 'Finished GET, trying next file.'

			if not response:
				print 'response is None or empty, FAIL'
				return False
			code = int(response.split('\r\n')[0].strip().split(' ')[1])
			if code != 200:
				print 'Expected a 200 in the persistent case; got %d' % (code)
				return False
			if 'Connection: close' in response or 'Connection: Close' in response:
				print 'Server closed the connection, but requested keep-alive.'
				return False

		sock.close()
		return True
 
	
def parse_flags(argv):
	arg_map = {}
	if len(argv) <= 1: return {}
	for arg in argv[1:]:
		bits = arg.split('=')
		if len(bits) == 2:
			arg_map[bits[0]] = bits[1]
	return arg_map

		
if __name__  == '__main__':
	arg_map = parse_flags(sys.argv)
	if ('--host' not in arg_map) or ('--port' not in arg_map) or ('--sslport' not in arg_map):
		print 'usage: test_client.py --host=linux2 --port=12345 --sslport=12346'
		sys.exit(-1)
	host = arg_map['--host']
	port = arg_map['--port']
	sslport = arg_map['--sslport']

	print 'HTTP tests!'
	NonPersistentTestRunner(host, port, 'http').run_all_tests()

	print '\n\nHTTPS tests!'
	NonPersistentTestRunner(host, sslport, 'https').run_all_tests()

	print '\n\nPersistent tests!'
	PersistentTestRunner(host, port).test_200()